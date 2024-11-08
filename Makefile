# Makefile
# A very simple makefile for compiling a Java program.  This file compiles
# the sim_cache.java file, and relies on javac to compile all its 
# dependencies automatically.
#
# If you require any special options to be passed to javac, modify the
# CFLAGS variable.  You may want to comment out the DEBUG option before
# running your simulations.

JAVAC = javac
JAVA = java
CLASS_FILES = *.class
MAIN_CLASS = sim_cache
# <BLOCKSIZE> <L1_SIZE> <L1_ASSOC> <L2_SIZE> <L2_ASSOC> <REPLACEMENT_POLICY> <INCLUSION_PROPERTY> <trace_file>
ARGS = 32 1048576 32768 0 0 0 0 traces/gcc_trace.txt
# DEBUG = -g
CFLAGS = $(DEBUG) -deprecation

# default target
all: compile

# compile
compile:
	$(JAVAC) *.java

# Run main class with preset args
run: $(CLASS_FILES)
	$(JAVA) $(MAIN_CLASS) $(ARGS)

sim_cache:
	$(JAVAC) $(CFLAGS) sim_cache.java
	
# type "make clean" to remove all your .class files
clean:
	-rm *.class
