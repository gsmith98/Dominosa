#!/bin/bash

javac PuzzleCreator.java

for i in `seq 1 16`
    do
        for n in `seq 1 25`;
            do
                java PuzzleCreator $i ./output.txt 1 >> ${i}-1-time_and_trys.txt
                java PuzzleCreator $i ./output.txt 4 >> ${i}-4-time_and_trys.txt
                java PuzzleCreator $i ./output.txt 8 >> ${i}-8-time_and_trys.txt
            done
    done 

for i in `seq 1 5`
    do 
	THREADS=1
        while [ $THREADS -le 16 ]
	    do
		java PuzzleCreator 10 output.txt $THREADS >> create20parallel${THREADS}.txt
		let THREADS=THREADS*2
	    done
    done


