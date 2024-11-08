import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

class sim_cache {
	public static void main(String[] args) {
		// sim_cache  <BLOCKSIZE> <L1_SIZE> <L1_ASSOC> <L2_SIZE> <L2_ASSOC> <REPLACEMENT_POLICY> <INCLUSION_PROPERTY> <trace_file>
		int blocksize = -1;
		int l1Size = -1;
		int l1Assoc = -1;
		int l2Size = -1;
		int l2Assoc = -1;
		int policy = -1; // 0 = LRU, 1 = FIFO, 2 = optimal
		int inclusion = 0; // 0 = non-inclusive, 1 = inclusive
		String file = "NULL";
		HashMap<Map.Entry<String, Integer>, Queue<Integer>> optimalMapL1 = null;
		HashMap<Map.Entry<String, Integer>, Queue<Integer>> optimalMapL2 = null;

		// Initialize variables
		float l1MissRate = 0; // (Reads + Writes) / (ReadMisses + WriteMisses)
		float l2MissRate = 0; // (Reads + Writes) / (ReadMisses + WriteMisses)
		int totalMemTraffic = 0; // Blocks traveling to or from main memory
		// Each command has an address and a cmd code 'r' or 'w'
		List<Command> commands = new ArrayList<Command>();

		// Capture command-line arguments
		if (args.length == 8) {
				blocksize = Integer.parseInt(args[0]);
				l1Size = Integer.parseInt(args[1]);
				l1Assoc = Integer.parseInt(args[2]);
				l2Size = Integer.parseInt(args[3]);
				l2Assoc = Integer.parseInt(args[4]);
				policy = Integer.parseInt(args[5]);
				inclusion = Integer.parseInt(args[6]);
				file = args[7];
		}
		else {
			System.out.println("Invalid arguments");
		}

		if (policy == 2)
		{
			optimalMapL1 = new HashMap<Map.Entry<String, Integer>, Queue<Integer>>();
			optimalMapL2 = new HashMap<Map.Entry<String, Integer>, Queue<Integer>>();
		}
		
		// Initialize Caches
		Cache l1Cache = new Cache(blocksize, l1Size, l1Assoc, policy, inclusion, 1, null, null, optimalMapL1);
		Cache l2Cache; // L2 always exists, but if it's size is zero, L1 won't have access to it
		
		if (l2Size > 0) {
			l2Cache = new Cache(blocksize, l2Size, l2Assoc, policy, inclusion, 2, null, l1Cache, optimalMapL2);
			l1Cache.nextLvl = l2Cache;
		}
		else {
			l2Cache = new Cache(blocksize, 0, 0, policy, inclusion, 2, null, null, optimalMapL2);
		}

		// Scan input file for commands
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			int i = 1;
			while((line = reader.readLine()) != null) {
				String[] splitCommand = line.split(" ");
				char cmd = splitCommand[0].charAt(0);
				String addr = splitCommand[1];
				commands.add(new Command(cmd, addr));

				if (policy == 2) { // preprocessing of commands to create optimal policy map
					Queue<Integer> pairAccesses; // Each tag has a queue of when it is accessed
					String currentTagL1 = Cache.calcTag(addr, l1Cache.tagBits);
					Integer currentIndexL1 = Cache.calcIndex(addr, l1Cache.tagBits, l1Cache.indexBits);
					Map.Entry<String, Integer> pairL1 = new AbstractMap.SimpleEntry<>(currentTagL1, currentIndexL1);
					
					if (optimalMapL1.containsKey(pairL1)) { // address has been accessed before
						pairAccesses = optimalMapL1.get(pairL1);
						pairAccesses.add(i); // Insert clockcycle into queue
					}
					else { // Address is accessed for the first time
						// Create new queue and add first access to it
						pairAccesses = new LinkedList<Integer>(); 
						pairAccesses.add(i);
						// Add new address and corresponding value Queue to map
						optimalMapL1.put(pairL1, pairAccesses); 
					}
					// System.out.println("address: " + addr + " tag: " + currentTagL1 + " index: " + currentIndexL1);
					// for (Integer a : pairAccesses) {
					// 	System.out.print(a + "	");
					// }
					// System.out.println("");
					if (l2Cache.assoc > 0) {
						String currentTagL2 = Cache.calcTag(addr, l2Cache.tagBits);
						Integer currentIndexL2 = Cache.calcIndex(addr, l2Cache.tagBits, l2Cache.indexBits);
						Map.Entry<String, Integer> pairL2 = new AbstractMap.SimpleEntry<>(currentTagL2, currentIndexL2);
						
						if (optimalMapL2.containsKey(pairL2)) { // address has been accessed before
							pairAccesses = optimalMapL2.get(pairL2);
							pairAccesses.add(i); // Insert clockcycle into queue
						}
						else { // Address is accessed for the first time
							// Create new queue and add first access to it
							pairAccesses = new LinkedList<Integer>(); 
							pairAccesses.add(i);
							// Add new address and corresponding value Queue to map
							optimalMapL2.put(pairL2, pairAccesses); 
						}
					}
					
				}
				i++;
			}
		}
		catch (IOException e) {
			System.out.println("File error: " + e.getMessage());
		}

		// Print initial setup parameters
		System.out.println("===== Simulator configuration =====");
		System.out.println("BLOCKSIZE:		" + blocksize);
		System.out.println("L1_SIZE:		" + l1Size);	
		System.out.println("L1_ASSOC:		" + l1Assoc);
		System.out.println("L2_SIZE:		" + l2Size);
		System.out.println("L2_ASSOC:		" + l2Assoc);
		if (policy == 0) {
			System.out.println("REPLACEMENT POLICY:	LRU");
		}
		else if (policy == 1) {
			System.out.println("REPLACEMENT POLICY:	FIFO");
		}
		else if (policy == 2) {
			System.out.println("REPLACEMENT POLICY:	optimal");
		}
		else {
			System.out.println("INVALID REPLACEMENT POLICY");
			return;
		}
		if (inclusion == 0) {
			System.out.println("INCLUSION PROPERTY:	non-inclusive");
		}
		else if (inclusion == 1) {
			System.out.println("INCLUSION PROPERTY:	inclusive");
		}
		else {
			System.out.println("INVALID INCLUSION PROPERTY");
			return;
		}
		System.out.println("trace_file:		" + file);

		int clockCycle = 1; // Iterate through commands an access caches
		for (Command command : commands) {
			// System.out.println("----------------------------------------");

			// String dir = null;
			// if (command.cmd == 'r') {
			// 	dir = "read";
			// }
			// else if (command.cmd == 'w') {
			// 	dir = "write";
			// }
			// else {
			// 	System.out.println("Invalid direction for command # " + clockCycle);
			// 	return;
			// }
			// System.out.println("# " + clockCycle + " : " + dir + " " + command.addr);
			// This is where the actual access takes place for each command
			l1Cache.access(command, clockCycle++);
			
			// debug code - delete later
			// int index = Cache.calcIndex(command.addr, l2Cache.tagBits, l2Cache.indexBits);
			// System.out.println("Index: " + index);
			// System.out.print("Set: ");
			// for (int i = 0; i < l2Cache.blocks[index].length; i++) {
			// 	if (l2Cache.blocks[index][i] != null)
			// 	System.out.print(l2Cache.blocks[index][i].address + " " + l2Cache.blocks[index][i].clockCycle + " ");
			// 	else
			// 	System.out.print("null  ");
			// }
			// System.out.println("");
	
		}

		if (l1Cache.numSets > 0) { // Print final cache contents
			System.out.println("===== L1 contents =====");
			for (int i = 0; i < l1Cache.numSets; i++) {
				System.out.print("Set	" + i + ":	");
				for (int j = 0; j < l1Assoc; j++) {
					Block block = l1Cache.blocks[i][j];
					if (block != null) {
						System.out.print(Cache.calcTag(block.address, l1Cache.tagBits));
						if (block.dirty == true) {
							System.out.print(" D	"); // D means dirty
						}
						else {
							System.out.print("	");
						}
					}
				}
				System.out.println("");
			}
		}
		if (l2Cache.numSets > 0) {
			System.out.println("===== L2 contents =====");
			for (int i = 0; i < l2Cache.numSets; i++) {
				System.out.print("Set	" + i + ":	");
				for (int j = 0; j < l2Assoc; j++) {
					Block block = l2Cache.blocks[i][j];
					if (block != null) {
						System.out.print(Cache.calcTag(block.address, l2Cache.tagBits));
						if (block.dirty == true) {
							System.out.print(" D	"); // D means dirty
						}
						else {
							System.out.print("	");
						}
					}
				}
				System.out.println("");
			}
		}

		// Calculate raw data based on cache performance
		if ((l1Cache.numReads + l1Cache.numWrites) > 0) {
			l1MissRate = (float)(l1Cache.numReadMisses + l1Cache.numWriteMisses) / (l1Cache.numReads + l1Cache.numWrites);
		}
		
		// Writebacks to next = total writebacks if no L2
		int l1WritebacksToNext = l1Cache.numWritebacks + l1Cache.numInvalWritebacks;

		if (l2Cache.numSets > 0) {
			l2MissRate = (float)(l2Cache.numReadMisses) / l2Cache.numReads;
			l1WritebacksToNext = l1Cache.numWritebacks;
			if (inclusion == 0) {
				totalMemTraffic = l2Cache.numReadMisses + l2Cache.numWriteMisses + l2Cache.numInvalWritebacks + l2Cache.numWritebacks;
			}
			else {
				totalMemTraffic = l2Cache.numReadMisses + l2Cache.numWriteMisses + l2Cache.numInvalWritebacks + l2Cache.numWritebacks + l1Cache.numInvalWritebacks;
			}
		}
		else {
			totalMemTraffic = l1Cache.numReadMisses + l1Cache.numWriteMisses + l1Cache.numWritebacks + l1Cache.numInvalWritebacks;
		}
		
		// Print raw results
		System.out.println("===== Simulation results (raw) =====");
		System.out.println("a. number of L1 reads:			" + l1Cache.numReads);
		System.out.println("b. number of L1 read misses:		" + l1Cache.numReadMisses);
		System.out.println("c. number of L1 writes:			" + l1Cache.numWrites);
		System.out.println("d. number of L1 write misses:		" + l1Cache.numWriteMisses);
		
		String formatL1MR;
		if (l1MissRate > 0) {
			formatL1MR = String.format("e. L1 miss rate:			%.6f", l1MissRate);
		}
		else {
			formatL1MR = "e. L1 miss rate:			0";
		}
		System.out.println(formatL1MR);

		System.out.println("f. number of L1 writebacks:		" + l1WritebacksToNext);
		System.out.println("g. number of L2 reads:			" + l2Cache.numReads);
		System.out.println("h. number of L2 read misses:		" + l2Cache.numReadMisses);
		System.out.println("i. number of L2 writes:			" + l2Cache.numWrites);
		System.out.println("j. number of L2 write misses:		" + l2Cache.numWriteMisses);
		
		String formatL2MR;
		if (l2MissRate > 0) {
			formatL2MR = String.format("k. L2 miss rate:			%.6f", l2MissRate);
		}
		else {
			formatL2MR = "k. L2 miss rate:			0";
		}
		System.out.println(formatL2MR);

		System.out.println("l. number of L2 writebacks:		" + (l2Cache.numWritebacks + l2Cache.numInvalWritebacks));
		System.out.println("m. total memory traffic:		" + totalMemTraffic);
	}
}
