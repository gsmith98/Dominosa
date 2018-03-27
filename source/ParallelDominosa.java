import java.util.ArrayList;
import java.util.Collections;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Random;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class ParallelDominosa {


    //The grid of numbers 0 through n that represents the puzzle
    protected static int[][] grid;

    //The set of all the Dominoes chosen to be in the solution (for creation)
    // private static ArrayList<DominoLoc> chosenDominoes;

    //The set of all possible Domino Locations
    protected static ArrayList<DomLoc> setS;

    //An arraylist of arraylists. Each list is the domlocs that have a certain pair
    protected static ArrayList<ArrayList<DomLoc>> pairMappings;

    //The grid as a 2d array of more informative square objects. Has info to help solve.
    protected static Square[][] superGrid;

    protected static boolean madeChange;


    public static void main(String[] args) {

        int n = Integer.parseInt(args[0]);
        String filename = args[1];
        int numthreads = Integer.parseInt(args[2]);

        grid = new int [n+1][n+2];
        loadPuzzle(filename);


        long runstart;
        runstart = System.currentTimeMillis();

        /**** SERIAL STUFF *****/

        //The set of all possible Domino Locations
        setS = new ArrayList<>();

        //loading all possible dom locs of set s
        DomLoc dom;

        //horizontal dominoes
        for(int r = 0; r < grid.length; r++) {
            for(int c = 0; c < grid[0].length-1; c++) {
                dom = new DomLoc(r, c, r, c+1, grid[r][c], grid[r][c+1]);
                setS.add(dom); 
            }
        }

        //vertical dominoes
        for(int c = 0; c < grid[0].length; c++) {
            for(int r = 0; r < grid.length-1; r++) {
                dom = new DomLoc(r, c, r+1, c, grid[r][c], grid[r+1][c]);
                setS.add(dom); 
            }
        }
        //setS is now full of all possible DomLocs

        //an arraylist of arraylists. Each index corresponds to a pair, and each list at that index is
        //a list of DomLocs that have that pair.
        pairMappings  = new ArrayList<ArrayList<DomLoc>>(DCOUNT(n));

        for(int i = 0; i < DCOUNT(n); i++) {
            pairMappings.add(new ArrayList<DomLoc>());
        }

        //System.out.println("SIZE: " + pairMappings.size());

        //now we put each DomLoc into pairMappings
        for(DomLoc d : setS) {
            int n1 = d.n1;
            int n2 = d.n2;

            int index = DINDEX(n1, n2);
            pairMappings.get(index).add(d);
        }

        //printPairMappings(pairMappings);

        //Now we make a 2D array of square objects, called superGrid
        superGrid = new Square[grid.length][grid[0].length];

        for(int c = 0; c < grid[0].length; c++) {
            for(int r = 0; r < grid.length; r++) {
                superGrid[r][c] = new Square(grid[r][c], r,c,findDoms(r,c));
            }
        }

        //now we have pairmappings and supergrid. Our method for solving uses them as follows:
        //1) Search pair mappings for a pair for which there is ony one DomLoc still marked as available
            //i.e. pairmappings has only one 2 - 0 left, so there's only one place we can chose 2 - 0
        //2) Search superGrid for squares that only have one available domloc in their array of 4 left
            //i.e. only one domloc is left that can use this square, so its the only way to cover this
        //3) repeat 1 and 2 alternatingly until either we have chosen DCOUNT doms or we reach a deadend

        madeChange = true;
        int chosenDoms = 0;
        while(madeChange) {
            madeChange = false;

            //search pairmappings for pairs only findable in one place----------------------------

            /**** PARALLEL STUFF - Pair Mapping *****/

            Thread [] threads = new Thread[numthreads];

            int numTodo = (int)(Math.ceil(pairMappings.size()/(double)numthreads));

            for(int i = 0; i < numthreads; i++) {
                threads[i] = new Thread(new ParallelPairMapping(i, numTodo));
                threads[i].start();
            }

            //.join threads
            for (int i = 0; i < numthreads; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {  
                    System.out.println("ERROR in thread");
                    return;
                }
            }

            /**** PARALLEL STUFF - Super Grid *****/
            

            threads = new Thread[numthreads];

            int rowsTodo = (int)Math.ceil(grid.length / (double)numthreads);

            for(int i = 0; i < numthreads; i++) {
                threads[i] = new Thread(new ParallelSuperGrid(i, rowsTodo));
                threads[i].start();
            }

            //.join threads
            for (int i = 0; i < numthreads; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {  
                    System.out.println("ERROR in thread");
                    return;
                }
            }

        } //END PARALLEL STUFF ----
        
        /**** SERIAL STUFF *****/

        chosenDoms = 0;

        for(DomLoc dl : setS) {
            if(dl.isChosen) {
                chosenDoms++;
            }
        }

        if(chosenDoms == DCOUNT(n)) {
           System.out.println("solution found!!!");
        }

        // Output search time
        long elapsed = System.currentTimeMillis() - runstart;
        System.out.println ( "Solved with n=" + n + " and " + numthreads + " threads" +
            " in " + elapsed + " milliseconds.");

    }

//an object representing a square in the grid. Contains information relevant to that square
    static class Square {

        protected int num; //number on the square
        protected boolean isCovered; //have we chosen a DomLoc that uses this square yet?
        protected int r; //row
        protected int c; //column
        protected DomLoc[] domlocs; //array of the up to 4 DomLocs that can use this square

        public Square(int num, int r, int c, DomLoc[] domlocs) {
            isCovered = false;
            this.num = num;
            this.r = r;
            this.c = c;
            this.domlocs = domlocs;

        }
    }

    //given a row column, finds all dominos in setS that use that square, returns as an array
    public static DomLoc[] findDoms(int r, int c) {
        DomLoc[] locs = new DomLoc[4];

        int index = 0;

        for ( DomLoc dl : setS) {
            if((dl.r1 == r && dl.c1 == c ) || (dl.r2 == r && dl.c2 == c )) {
                locs[index] = dl;
                index++;
            }
        }
        return locs;
    }

    //An object representing a possible location of a domino. Holds relevant info.
    //This is the solver's version of representing this idea
    static class DomLoc {

        protected boolean isAvailable; //is this DomLoc still valid for use?
        private boolean isChosen; //is this Domino chosen as part of the solution?
        private int r1, r2, c1, c2; //2 rows and columns of the 2 points in the possible domino
        private int n1, n2; //2 numbers on the domino at points 1 and 2 respectively

        public DomLoc(int r1, int c1, int r2, int c2, int n1, int n2) {
            this.r1 = r1;
            this.r2 = r2;
            this.c1 = c1;
            this.c2 = c2;
            this.n1 = n1;
            this.n2 = n2;

            isAvailable = true;
            isChosen = false;
        }

        public String toString() {
            return ("[(" + r1 + "," + c1 + ") --> " + n1 + " || " + "(" + r2 + "," + c2 + ") --> " + n2 + "]");
        }

    }



    //chooses a given domloc. Marks it as chosen.
    //marks squares on it as covered
    //marks overlapping domlocs as unavailable (includes this one)
    //marks domlocs that have same pair of numbers as unavailable
    public static void chooseDomLoc(DomLoc dl) {
        dl.isChosen = true;
        superGrid[dl.r1][dl.c1].isCovered = true;
        superGrid[dl.r2][dl.c2].isCovered = true;

        for (DomLoc olap : superGrid[dl.r1][dl.c1].domlocs) {
            if (olap != null) {
                olap.isAvailable = false;
            }
        }
        for (DomLoc olap : superGrid[dl.r2][dl.c2].domlocs) {
            if (olap != null) {
                olap.isAvailable = false;
            }
        }

        for (DomLoc conflict: pairMappings.get(DINDEX(dl.n1,dl.n2))){
            conflict.isAvailable = false;
        }

    }
   
   //finds the nth triangle number
    public static int TRI(int n) {
        return (n * (n + 1) / 2);
    }

    //finds the number of dominoes in a puzzle with numbers 0 through n
    public static int DCOUNT(int n) {
        return TRI(n+1);
    }

    //takes an unordered pair of numbers and maps them to a unique index 0 through DCOUNT(n)-1
    public static int DINDEX(int n1, int n2) {
        return TRI(Math.max(n1,n2)) + Math.min(n1, n2);
    } 


    public static void loadPuzzle(String filename) {

        try {

            Scanner br = new Scanner(new File(filename));

            StringBuilder sb = new StringBuilder();
            String line = br.nextLine();

            int r = 0;
            int c = 0;

            while (line != null) {
                
                String[] row = line.split("\\s+");
                for(String s : row) {
                    grid[r][c] = Integer.parseInt(s);
                    c++;
                }
                // sb.append(line);
                // sb.append(System.lineSeparator());
                line = br.nextLine();
                r++;
                c = 0;
            }
            br.close();

            // String everything = sb.toString();
        } catch (Exception e) {
            // continue;
        }


    }  

}