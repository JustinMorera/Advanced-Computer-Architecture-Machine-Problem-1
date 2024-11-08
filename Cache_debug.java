import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class Cache_debug { // When running, remember to change sim_cache caches to correct class
    // Parameters passed in from command line arguments
    int blocksize; // Used to calculate numSets
    int cacheSize; // Used to calculate numSets
    int assoc; // Used to calculate numSets and number of columns in blocks array
    int policy; // Controls type of eviction-selection method
    int inclusion; // Controls whether L1 blocks are invalidated on L2 eviction
    HashMap<Map.Entry<String, Integer>, Queue<Integer>> optimalMap;

    // Calculated from parameters
    int numSets;
    int offsetBits;
    int indexBits;
    int tagBits;

    int level; // For easy printing
    // References to connected cache blocks
    Cache nextLvl; // null means connected to main memory
    Cache prevLvl; // null means L1 cache
    Block[][] blocks; // Actual block storage

    // Performance tracking
    int numReads;
    int numReadMisses;
    int numWrites;
    int numWriteMisses;
    int numWritebacks;
    int numInvalWritebacks; // Writebacks to main memory only from invalidation

    public Cache_debug(int blocksize, int cacheSize, int assoc, int policy, int inclusion, int level, Cache nextLvl, Cache prevLevel, HashMap<Map.Entry<String, Integer>, Queue<Integer>> optimalMap) {
        this.blocksize = blocksize;
        this.cacheSize = cacheSize;
        this.assoc = assoc;
        this.policy = policy;
        this.inclusion = inclusion;
        this.level = level;
        this.nextLvl = nextLvl;
        this.prevLvl = prevLevel;
        this.optimalMap = optimalMap;

        this.numReads = 0;
        this.numReadMisses = 0;
        this.numWrites = 0;
        this.numWriteMisses = 0;
        this.numWritebacks = 0;
        this.numInvalWritebacks = 0;

        if (assoc > 0 && blocksize > 0) {
            this.numSets = cacheSize / (assoc * blocksize);
        }
        else {
            this.numSets = 0;
        }

        this.offsetBits = (int)(Math.log(blocksize) / Math.log(2));
        this.indexBits = (int)(Math.log(this.numSets) / Math.log(2));
        this.tagBits = 32 - this.offsetBits - this.indexBits;

        this.blocks = new Block[numSets][assoc];
    }

    // Input: A String representing a block's hex address and the # of bits used for tag
    // Output: A String representing only the block's tag in hexadecimal
    public static String calcTag(String hexAddr, int tagBits) {
        // Convert address to binary
        String binaryAddr = Integer.toBinaryString(Integer.parseInt(hexAddr, 16));

        // Pad with leading 0's until 32 bits
        if (binaryAddr.length() < 32) {
            binaryAddr = String.format("%32s", binaryAddr).replace(' ', '0');
        }

        // Get only the first {tagBits} number of bits
        String binaryTag = binaryAddr.substring(0, tagBits);
        
        // Pad with trailing 0's until 32 bits
        // if (binaryTag.length() < 32) {
        //     binaryTag = String.format("%-32s", binaryTag).replace(' ', '0');
        // }

        // Convert tag to decimal
        int tag = Integer.parseInt(binaryTag, 2);

        // Convert tag to hex String and return
        String hexTag = Integer.toHexString(tag);
        return hexTag;
    }
    
    // Input: A String representing a block's hex address, an int number of bits
    // used for tag, and an int number of  bits used for index
    // Output: A String representing only the block's index in hexadecimal
    public static int calcIndex(String hexAddr, int tagBits, int indexBits) {
        // Convert address to binary
        String binaryAddr = Integer.toBinaryString(Integer.parseInt(hexAddr, 16));

        // Pad with leading 0's until 32 bits
        if (binaryAddr.length() < 32) {
            binaryAddr = String.format("%1$32s", binaryAddr).replace(' ', '0');
        }

        // Get only the middle bits in range ({tagBits}, {indexBits}]
        String binaryIndex = binaryAddr.substring(tagBits, tagBits + indexBits);

        // Convert index to decimal and return
        int index = Integer.parseInt(binaryIndex, 2);
        return index;
    }

    // On L2 eviction (happens when read or write request misses at the
    // L2 cache and the requested block needs to be allocated). When a 
    // victim block in the L2 cache needs to be evicted, the L2 cache 
    // must invalidate the corresponding block in L1 as well (assuming 
    // it exists there). If the L1 block that needs to be invalidated is 
    // dirty, a write of the block will be issued to the main memory directly.
    // Input: A String representing a block's hex address
    // Output: No output, marks the block passed in as invalid
    public void invalidate(String address) {
        // Get block's tag and index
        String tag = calcTag(address, this.tagBits);
        int index = calcIndex(address, this.tagBits, this.indexBits);

        // Convert address to binary
        String binaryAddr = Integer.toBinaryString(Integer.parseInt(address, 16));
        // Pad with leading 0's until 32 bits
        if (binaryAddr.length() < 32) {
            binaryAddr = String.format("%1$32s", binaryAddr).replace(' ', '0');
        }
        // Get only the last offset bits and convert to decimal
        // int offset = Integer.parseInt(binaryAddr.substring(this.tagBits + this.indexBits), 2);
        // Convert address to decimal and subtract offset value then convert address to hex
        // String addrWithoutOffset = Integer.toHexString(Integer.parseInt(binaryAddr, 2) - offset);
        
        // Iterate through every block in the {index} set
        for (int i = 0; i < this.blocks[index].length; i++) {
            Block cell = this.blocks[index][i];
            // Compare tag of current block to target tag
            String cellTag = calcTag(cell.address, this.tagBits);
            if (cell != null && cellTag.equals(tag)) {
                // System.out.print("L" + this.level + " invalidated: " + addrWithoutOffset + " (tag " + tag + ", index " + index);
                cell.valid = false; // Invalidate block
                if (cell.dirty == true) {
                    // System.out.print(", dirty");
                }
                else {
                    // System.out.print(", clean");
                }
                // System.out.println(")");
                if (cell.dirty == true) { // If dirty, writeback to main memory
                    // System.out.println("L" + this.level + " writeback to main memory directly");
                    this.numInvalWritebacks++;
                    cell.dirty = false; // Mark clean to avoid future writebacks
                }
            }
        }
    }

    // Input: A Command with hex String address and 'r' or 'w' cmd, an int clockCycle
    // Output: Void
    public void access(Command command, int clockCycle) {
        // System.out.println("L" + this.level + " Current blocks:");
        // for (int i = 0; i < this.blocks.length; i++) {
        //     for (int j = 0; j < this.blocks[i].length; j++) {
        //         if (this.blocks[i][j] != null)
        //             System.out.print(this.blocks[i][j].address + "    " + this.blocks[i][j].clockCycle + "  ");
        //         else
        //             System.out.print("null  ");
        //     }
        //     System.out.println("");
        // }

        // debug code - delete later
        // System.out.println("Target: " + command.addr + ", tag: " + calcTag(command.addr, this.tagBits));
        // For printing read or write
        // String dir = null;
        if (command.cmd == 'r') {
            this.numReads++;
            // dir = "read";
        }
        else {
            this.numWrites++;
            // dir = "write";
        }

        // Convert target address to binary
        String binaryAddr = Integer.toBinaryString(Integer.parseInt(command.addr, 16));
        // Pad with leading 0's until 32 bits
        if (binaryAddr.length() < 32) {
            binaryAddr = String.format("%1$32s", binaryAddr).replace(' ', '0');
        }

        // Get tag, index, and offset
        String tag = calcTag(command.addr, this.tagBits);
        int index = calcIndex(command.addr, this.tagBits, this.indexBits);
        Map.Entry<String, Integer> pair; // optimal only

        // String tag = Integer.toHexString(Integer.parseInt(command.addr, 16) / (this.blocks.length * blocksize));
        // int index = (int)(Integer.parseInt(command.addr, 16) / blocksize) % this.blocks.length;
        // int offset = Integer.parseInt(binaryAddr.substring(this.tagBits + this.indexBits), 2);
        // String addrWithoutOffset = Integer.toHexString(Integer.parseInt(binaryAddr, 2) - offset);

        // Write-allocate: Both write misses and read misses cause blocks to be allocated
        // Initialize block to allocate
        Block block = new Block(clockCycle, command.addr);
        Block victim = null;
        Block cell = null;
        // System.out.println("L" + this.level + " " + dir + " : " + addrWithoutOffset + " (tag " + tag + ", index " + index + ")");
        
        // LRU: Replace the block that was least recently touched (updated on hits and misses).
        if (policy == 0) {
            int lruIndex = -1, lruCycle = Integer.MAX_VALUE;
            for (int i = 0; i < this.blocks[index].length; i++) { // search for target block  
                cell = this.blocks[index][i];
                // Search for target tag
                // Non-empty block found, compare tags
                if (cell != null) {
                    String cellTag = calcTag(cell.address, this.tagBits);
                    if (cellTag.equals(tag)) { // hit
                        if (cell.valid == true) {
                        // System.out.println("New clockcycle: " + clockCycle);
                        cell.clockCycle = clockCycle; // Update LRU
                        // debug code - delete later
                        // System.out.println("Hit clockcycle: " + cell.clockCycle);
                        // System.out.println("Compare tags: " + cell.address + " = " + calcTag(cell.address, this.tagBits));
                        // System.out.println("Compare tags: " + command.addr + " = " + calcTag(command.addr, this.tagBits));
                        // System.out.println("L" + this.level + " hit");
                        // System.out.println("L" + this.level + " update LRU");
                        if (command.cmd == 'w') { // Mark matching block dirty
                            cell.dirty = true;
                            // System.out.println("L" + this.level + " set dirty");
                        } // hit
                        return;
                        }
                        else { // Cache hit on invalid block, immediately replace
                            lruIndex = i;
                            lruCycle = cell.clockCycle;
                            break;
                        }
                    }
                }
            }
            if (lruIndex == -1) { // Only continue searching if we haven't found a match
                for (int i = 0; i < this.blocks[index].length; i++) { // search for LRU block
                    cell = this.blocks[index][i];
                    if (cell == null) { // empty block found
                        if (command.cmd == 'r') {
                            this.numReadMisses++;
                        }
                        else {
                            this.numWriteMisses++;
                        }

                        // System.out.println("L" + this.level + " miss");
                        // System.out.println("L" + this.level + " victim: none");
                        // If there is at least one invalid block in the set, then 
                        // there is already space for the requested block X and no 
                        // further action is required: Issue a read of the requested 
                        // block X to the next level of the memory hierarchy and put 
                        // the requested block X in the appropriate place in the set
                        if (this.nextLvl != null) {
                            Command tmp = new Command('r', command.addr);
                            this.nextLvl.access(tmp, ++clockCycle);
                        }
                        // System.out.println("L" + this.level + " update LRU");
                        if (command.cmd == 'w') { // Mark new block dirty
                            block.dirty = true;
                            // System.out.println("L" + this.level + " set dirty");
                        } // miss

                        this.blocks[index][i] = block; // Insert block into set
                        return;
                    }
                    else if (cell.valid == false) { // If invalid, must evict
                        lruIndex = i;
                        lruCycle = cell.clockCycle;
                        break;
                    }
                    // Non-empty, non-matching, valid block
                    else if (cell.clockCycle < lruCycle) { // track LRU
                        lruIndex = i;
                        lruCycle = cell.clockCycle;
                    }
                }
            }

            // Eviction - Evict LRU block
            // System.out.println("L" + this.level + " miss");
            victim = this.blocks[index][lruIndex];

            // Convert victim address to binary
            String binaryVictim = Integer.toBinaryString(Integer.parseInt(victim.address, 16));
            // Pad with leading 0's until 32 bits
            if (binaryVictim.length() < 32) {
                binaryVictim = String.format("%1$32s", binaryVictim).replace(' ', '0');
            }

            // Get victim tag, index, and offset
            // String victTag = calcTag(victim.address, this.tagBits);
            // int victIndex = calcIndex(victim.address, this.tagBits, this.indexBits);
            // String victTag = Integer.toHexString(Integer.parseInt(victim.address, 16) / (this.blocks.length * blocksize));
            // int victIndex = (int)(Integer.parseInt(victim.address, 16) / blocksize) % this.blocks.length;
            // int victOffset = Integer.parseInt(binaryVictim.substring(this.tagBits + this.indexBits), 2);
            
            // String victimWithoutOffset = Integer.toHexString(Integer.parseInt(binaryVictim, 2) - victOffset);
            // System.out.print("L" + this.level + " victim: " + victimWithoutOffset + " (tag " + victTag + ", index " + victIndex);
            
            if (victim.dirty == true) { // If victim dirty, writeback
                // System.out.print(", dirty");
                this.numWritebacks++;
            }
            else {
                // System.out.print(", clean");
            }
            if (victim.valid == false) {
                // System.out.print(", invalid");
            }
            // System.out.println(")");
            
            // Inclusive policy w/ valid victim and previous level cache
            if (inclusion == 1 && victim != null && victim.valid == true && this.prevLvl != null) {
                // Must invalidate lower-level victim upon eviction
                this.prevLvl.invalidate(victim.address); 
            }
            // If this victim is dirty, then a write of the victim block 
            // must be issued to the next level of the memory hierarchy
            if (this.nextLvl != null) {
                Command tmp;
                if (victim.dirty == true && victim.valid == true) {
                    tmp = new Command('w', victim.address);
                    this.nextLvl.access(tmp, ++clockCycle);
                    victim.dirty = false; // Mark clean, just in case
                } // Issue read of requested block to next level
                tmp = new Command('r', block.address);
                this.nextLvl.access(tmp, ++clockCycle);
            }
            
            // System.out.println("L" + this.level + " update LRU");
            victim.valid = false; // Mark invalid, just in case
            
            if (command.cmd == 'w') {
                block.dirty = true; // Mark new block dirty
                // System.out.println("L" + this.level + " set dirty");
            } // miss-eviction

            if (command.cmd == 'r') {
                this.numReadMisses++;
            }
            else {
                this.numWriteMisses++;
            }

            // debug code - delete later
            // if (victim != null) {
            //     System.out.println("L" + this.level + " Victim: " + victim.address + ", tag: " + victTag);
            // }
            this.blocks[index][lruIndex] = block; // Insert block into set
            return;
        }

        //FIFO: Replace the block that was placed first in the cache.
        else if (policy == 1) {
            int firstIndex = -1, firstCycle = Integer.MAX_VALUE;
            for (int i = 0; i < this.blocks[index].length; i++) { // search for target block  
                cell = this.blocks[index][i];
                // Search for target tag
                // Non-empty block found, compare tags
                if (cell != null) {
                    String cellTag = calcTag(cell.address, this.tagBits);
                    if (cellTag.equals(tag) && cell.valid == true) { // hit
                        // System.out.println("L" + this.level + " hit");
                        // System.out.println("L" + this.level + " update FIFO");
                        if (command.cmd == 'w') { // Mark matching block dirty
                            cell.dirty = true;
                            // System.out.println("L" + this.level + " set dirty");
                        } // hit
                        return;
                    }
                    else if (cell.valid == false) { // Cache hit on invalid block, immediately replace
                        firstIndex = i;
                        firstCycle = cell.clockCycle;
                        break;
                    }
                }
            }
            if (firstIndex == -1) { // Only continue searching if we haven't found a match
                for (int i = 0; i < this.blocks[index].length; i++) { // search for LRU block
                    cell = this.blocks[index][i];
                    if (cell == null) { // empty block found
                        if (command.cmd == 'r') {
                            this.numReadMisses++;
                        }
                        else {
                            this.numWriteMisses++;
                        }

                        // System.out.println("L" + this.level + " miss");
                        // System.out.println("L" + this.level + " victim: none");
                        // If there is at least one invalid block in the set, then 
                        // there is already space for the requested block X and no 
                        // further action is required: Issue a read of the requested 
                        // block X to the next level of the memory hierarchy and put 
                        // the requested block X in the appropriate place in the set
                        if (this.nextLvl != null) {
                            Command tmp = new Command('r', command.addr);
                            this.nextLvl.access(tmp, ++clockCycle);
                        }
                        // System.out.println("L" + this.level + " update FIFO");
                        if (command.cmd == 'w') { // Mark new block dirty
                            block.dirty = true;
                            // System.out.println("L" + this.level + " set dirty");
                        } // miss

                        this.blocks[index][i] = block; // Insert block into set
                        return;
                    }
                    else if (cell.valid == false) { // If invalid, must evict
                        firstIndex = i;
                        firstCycle = cell.clockCycle;
                        break;
                    }
                    // Non-empty, non-matching, valid block
                    else if (cell.clockCycle < firstCycle) { // track LRU
                        firstIndex = i;
                        firstCycle = cell.clockCycle;
                    }
                }
            }
            // Eviction - FI block
            // System.out.println("L" + this.level + " miss");
            victim = this.blocks[index][firstIndex];

            // Convert victim address to binary
            String binaryVictim = Integer.toBinaryString(Integer.parseInt(victim.address, 16));
            // Pad with leading 0's until 32 bits
            if (binaryVictim.length() < 32) {
                binaryVictim = String.format("%1$32s", binaryVictim).replace(' ', '0');
            }

            // Get victim tag, index, and offset
            // String victTag = calcTag(victim.address, this.tagBits);
            // int victIndex = calcIndex(victim.address, this.tagBits, this.indexBits);
            // int victOffset = Integer.parseInt(binaryVictim.substring(this.tagBits + this.indexBits), 2);
            
            // String victimWithoutOffset = Integer.toHexString(Integer.parseInt(binaryVictim, 2) - victOffset);
            // System.out.print("L" + this.level + " victim: " + victimWithoutOffset + " (tag " + victTag + ", index " + victIndex);
            
            if (victim.dirty == true) { // If victim dirty, writeback
                // System.out.print(", dirty");
                this.numWritebacks++;
            }
            else {
                // System.out.print(", clean");
            }
            if (victim.valid == false) {
                // System.out.print(", invalid");
            }
            // System.out.println(")");

            // Inclusive policy w/ valid victim and previous level cache
            if (inclusion == 1 && victim != null && victim.valid == true && this.prevLvl != null) {
                // Must invalidate lower-level victim upon eviction
                this.prevLvl.invalidate(victim.address);
            }

            // If this victim is dirty, then a write of the victim block 
            // must be issued to the next level of the memory hierarchy
            if (this.nextLvl != null) {
                Command tmp;
                if (victim.dirty == true && victim.valid == true) {
                    tmp = new Command('w', victim.address);
                    this.nextLvl.access(tmp, ++clockCycle);
                    victim.dirty = false; // Mark clean, just in case
                } // Issue read of requested block to next level
                tmp = new Command('r', command.addr);
                this.nextLvl.access(tmp, ++clockCycle);
            }

            // System.out.println("L" + this.level + " update FIFO");
            victim.valid = false; // Mark invalid, just in case

            if (command.cmd == 'w') {
                block.dirty = true; // Mark new block dirty
                // System.out.println("L" + this.level + " set dirty");
            } // miss-eviction

            if (command.cmd == 'r') {
                this.numReadMisses++;
            }
            else {
                this.numWriteMisses++;
            }

            this.blocks[index][firstIndex] = block; // Insert block into set
            return;
        }

        // Optimal: Replace the block that will be needed farthest in the future.
        else if (policy == 2) {
            pair = new AbstractMap.SimpleEntry<>(tag, index);
            Queue<Integer> accessesQ = null; // Our magical list of future accesses
            // This will need preprocessing the trace to determine reuse distance for 
            // each memory reference (i.e. how many accesses later we will need this 
            // cache block). You can then run the actual cache simulation on the output 
            // of the preprocessing stage. Note: If there is more than one block (in a 
            // set) that’s not going to be reused again in the trace, replace the 
            // leftmost one that comes up from the search.
            int optIndex = -1;
            Integer optCycle = Integer.MIN_VALUE;
            for (int i = 0; i < this.blocks[index].length; i++) { // search for target block  
                cell = this.blocks[index][i];
                // Search for target tag
                // Non-empty block found, compare tags
                if (cell != null) {
                    String cellTag = calcTag(cell.address, this.tagBits);
                    if (cellTag.equals(tag)) { // hit
                        if (cell.valid == true) {
                            accessesQ = this.optimalMap.get(pair);
                            // Integer nextAccess = accessesQ.peek();
                            // while (nextAccess != null && nextAccess <= clockCycle) {
                            //     accessesQ.poll(); // Update next access
                            //     nextAccess = accessesQ.peek();
                            // }
                            // if (nextAccess == null) {
                            //     cell.clockCycle = Integer.MAX_VALUE;
                            // }
                            // else {
                            //     cell.clockCycle = accessesQ.poll(); // Do one more time to get next access
                            // }
                            if (accessesQ.peek() != null) {
                                cell.clockCycle = accessesQ.poll();
                            }
                            else {
                                cell.clockCycle = Integer.MAX_VALUE;
                            }
                            // System.out.println("L" + this.level + " hit");
                            // System.out.println("L" + this.level + " update optimal");
                            // System.out.println("L" + this.level + " Current blocks at index " + index + ":");
                            // for (int j = 0; j < this.blocks[index].length; j++) {
                            //     if (this.blocks[index][j] != null)
                            //     System.out.print(this.blocks[index][j].address + "    " + this.blocks[index][j].clockCycle + "  ");
                            //     else
                            //     System.out.print("null  ");
                            // }
                            // System.out.println("");
                
                            if (command.cmd == 'w') { // Mark matching block dirty
                                cell.dirty = true;
                                // System.out.println("L" + this.level + " set dirty");
                            } // hit
                            return;
                        }
                        else { // Cache hit on invalid block, immediately replace
                            optIndex = i;
                            break;
                        }
                    }
                }
            }
            if (optIndex == -1) {
                for (int i = 0; i < this.blocks[index].length; i++) { // search for empty block
                    cell = this.blocks[index][i];
                    if (cell == null) { // empty block found
                        if (command.cmd == 'r') {
                            this.numReadMisses++;
                        }
                        else {
                            this.numWriteMisses++;
                        }
    
                        // System.out.println("L" + this.level + " miss");
                        // System.out.println("L" + this.level + " victim: none");
                        // If there is at least one invalid block in the set, then 
                        // there is already space for the requested block X and no 
                        // further action is required: Issue a read of the requested 
                        // block X to the next level of the memory hierarchy and put 
                        // the requested block X in the appropriate place in the set
                        accessesQ = this.optimalMap.get(pair);
                        Integer nextAccess = accessesQ.peek();
                        // System.out.println(nextAccess);
                        // while (nextAccess != null && nextAccess <= clockCycle) {
                        //     accessesQ.poll(); // Update next access
                        //     nextAccess = accessesQ.peek();
                        // }
                        if (nextAccess == null) {
                            block.clockCycle = Integer.MAX_VALUE;
                        }
                        else {
                            block.clockCycle = accessesQ.poll(); // Do one more time to get next access
                        }
                        if (this.nextLvl != null) {
                            Command tmp = new Command('r', command.addr);
                            this.nextLvl.access(tmp, ++clockCycle);
                        }
                        // System.out.println("L" + this.level + " update optimal");
                        if (command.cmd == 'w') { // Mark new block dirty
                            block.dirty = true;
                            // System.out.println("L" + this.level + " set dirty");
                        } // miss
    
                        this.blocks[index][i] = block; // Insert block into set
                        // System.out.println("L" + this.level + " Current blocks at index " + index + ":");
                        // for (int j = 0; j < this.blocks[index].length; j++) {
                        //     if (this.blocks[index][j] != null)
                        //     System.out.print(this.blocks[index][j].address + "    " + this.blocks[index][j].clockCycle + "  ");
                        //     else
                        //     System.out.print("null  ");
                        // }
                        // System.out.println("");

                        return;
                    }
                }
                for (int i = 0; i < this.blocks[index].length; i++) { // search for invalid or latest used block
                    cell = this.blocks[index][i];

                        if (cell.clockCycle <= clockCycle) { // Update next access
                            String cellTag = calcTag(cell.address, this.tagBits);
                            int cellIndex = calcIndex(cell.address, this.tagBits, this.indexBits);
                            Map.Entry<String, Integer> cellPair = new AbstractMap.SimpleEntry<>(cellTag, cellIndex);
                            accessesQ = this.optimalMap.get(cellPair);
                            Integer nextAccess = accessesQ.peek();
                            while (nextAccess != null && nextAccess <= clockCycle) {
                                accessesQ.poll(); // Update next access
                                nextAccess = accessesQ.peek();
                            }
                            if (nextAccess == null) {
                                cell.clockCycle = Integer.MAX_VALUE;
                            }
                            else {
                                cell.clockCycle = accessesQ.poll(); // Do one more time to get next access
                            }    
                        }

                        if (cell != null) {
                            if (cell.valid == false) { // If invalid, must evict
                                optIndex = i;
                                break;
                            }

                        // Non-empty, non-matching, valid block
                        if (cell.clockCycle == null || cell.clockCycle > optCycle) { // track LRU
                            optCycle = cell.clockCycle;
                            optIndex = i;
                            if (cell.clockCycle == null) {
                                break;
                            }
                        }
                    }
                }
    
            }
            
            // Eviction - Evict Most distantly used block
            // System.out.println("L" + this.level + " miss");
            victim = this.blocks[index][optIndex];

            // Convert victim address to binary
            String binaryVictim = Integer.toBinaryString(Integer.parseInt(victim.address, 16));
            // Pad with leading 0's until 32 bits
            if (binaryVictim.length() < 32) {
                binaryVictim = String.format("%1$32s", binaryVictim).replace(' ', '0');
            }

            // Get victim tag, index, and offset
            // String victTag = calcTag(victim.address, this.tagBits);
            // int victIndex = calcIndex(victim.address, this.tagBits, this.indexBits);
            // String victTag = Integer.toHexString(Integer.parseInt(victim.address, 16) / (this.blocks.length * blocksize));
            // int victIndex = (int)(Integer.parseInt(victim.address, 16) / blocksize) % this.blocks.length;
            // int victOffset = Integer.parseInt(binaryVictim.substring(this.tagBits + this.indexBits), 2);
            
            // String victimWithoutOffset = Integer.toHexString(Integer.parseInt(binaryVictim, 2) - victOffset);
            // System.out.print("L" + this.level + " victim: " + victimWithoutOffset + " (tag " + victTag + ", index " + victIndex);
            
            if (victim.dirty == true) { // If victim dirty, writeback
                // System.out.print(", dirty");
                this.numWritebacks++;
            }
            else {
                // System.out.print(", clean");
            }
            if (victim.valid == false) {
                // System.out.print(", invalid");
            }
            // System.out.println(")");
            
            // System.out.println("L" + this.level + " Current blocks at index " + index + ":");
            // for (int j = 0; j < this.blocks[index].length; j++) {
            //     if (this.blocks[index][j] != null)
            //     System.out.print(this.blocks[index][j].address + "    " + this.blocks[index][j].clockCycle + "  ");
            //     else
            //     System.out.print("null  ");
            // }
            // System.out.println("");

            // Inclusive policy w/ valid victim and previous level cache
            if (inclusion == 1 && victim != null && victim.valid == true && this.prevLvl != null) {
                // Must invalidate lower-level victim upon eviction
                this.prevLvl.invalidate(victim.address); 
            }

            // accessesQ = this.optimalMap.get(pair);
            // Integer nextAccess = accessesQ.peek();
            // while (nextAccess != null && nextAccess <= clockCycle) {
            //     accessesQ.poll(); // Update next access
            //     nextAccess = accessesQ.peek();
            // }
            // if (nextAccess == null) {
            //     block.clockCycle = Integer.MAX_VALUE;
            // }
            // else {
            //     block.clockCycle = accessesQ.poll(); // Do one more time to get next access
            // }
            // If this victim is dirty, then a write of the victim block 
            // must be issued to the next level of the memory hierarchy
            if (this.nextLvl != null) {
                Command tmp;
                if (victim.dirty == true && victim.valid == true) {
                    tmp = new Command('w', victim.address);
                    this.nextLvl.access(tmp, ++clockCycle);
                    victim.dirty = false; // Mark clean, just in case
                } // Issue read of requested block to next level
                tmp = new Command('r', block.address);
                this.nextLvl.access(tmp, ++clockCycle);
            }
            
            // System.out.println("L" + this.level + " update optimal");
            victim.valid = false; // Mark invalid, just in case
            
            if (command.cmd == 'w') {
                block.dirty = true; // Mark new block dirty
                // System.out.println("L" + this.level + " set dirty");
            } // miss-eviction

            if (command.cmd == 'r') {
                this.numReadMisses++;
            }
            else {
                this.numWriteMisses++;
            }

            if (victim != null)
            // System.out.println("Victim: " + victim.address + ", tag: " + victTag);
            
            this.blocks[index][optIndex] = block; // Insert block into set
            return;
        }
        return;
    }
}
