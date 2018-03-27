
import java.util.ArrayList;
import java.util.Collections;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Random;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class PuzzleCreator implements Runnable {

    private int n;
    private String filename;
    private int thread_id;
    private ArrayList<DominoLoc> chosenDominoes;

    //The set of all possible Domino Locations
    private ArrayList<DomLoc> setS;

    //An arraylist of arraylists. Each list is the domlocs that have a certain pair
    private ArrayList<ArrayList<DomLoc>> pairMappings;

    //The grid as a 2d array of more informative square objects. Has info to help solve.
    private Square[][] superGrid;


    private int[][] grid;

    public PuzzleCreator(int id, int n, String filename) {
        this.n = n;
        this.filename = Integer.toString(n) + filename;
        this.thread_id = id;
        this.grid = new int[n+1][n+2];
        chosenDominoes = new ArrayList<>();
        setS = new ArrayList<>();
        pairMappings = new ArrayList<ArrayList<DomLoc>>();
        superGrid = new Square[n+1][n+2];
    }

    static long runstart;

    public static void main(String[] args) {
        int n = Integer.parseInt(args[0]);
        String filename = args[1];
        int numthreads = Integer.parseInt(args[2]);

        Thread[] threads = new Thread[numthreads];

        runstart = System.currentTimeMillis();
        
        for(int i = 0; i < numthreads; i++) {
            threads[i] = new Thread(new PuzzleCreator(i, n, filename));
            threads[i].start();
        }


    }

    public void run () {

        boolean solveable = false;

        int counter = 1;



        while(!solveable) {
            long curr = System.nanoTime();
            createPuzzle(n);
            long elapsed = System.nanoTime() - curr;
            //System.out.println("1 " + elapsed);         
            solveable = solvePuzzle(n);
            long newElapsed = System.nanoTime() - curr - elapsed;
            double ratio = (double)((float)newElapsed/elapsed);
            //System.out.println("solver/creator: " + ratio + " | solve: " + newElapsed);         

            counter++;
        }

        // Output search time
        long elapsed = System.currentTimeMillis() - runstart;
        System.out.println(elapsed + " " + counter);

        try {
 
            File file = new File("filename");
 
            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
 
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            
            for(int r = 0; r < grid.length; r++) {
                bw.write("\n");
                for(int c = 0; c < grid[r].length; c++) {
                    bw.write(grid[r][c] + " ");
                }
            }
            

            bw.close();
 
            System.exit(0);
 
        } catch (IOException e) {
            e.printStackTrace();
        }

        


    }

        public boolean solvePuzzle(int n) {

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

            // System.out.println("SIZE: " + pairMappings.size());

            //now we put each DomLoc into pairMappings
            for(DomLoc d : setS) {
                int n1 = d.n1;
                int n2 = d.n2;

                int index = DINDEX(n1, n2);
                pairMappings.get(index).add(d);
            }

            // printPairMappings(pairMappings);

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

            boolean madeChange = true;
            int chosenDoms = 0;
            while(madeChange) {
                madeChange = false;

                //search pairmappings for pairs only findable in one place----------------------------
                for(ArrayList<DomLoc> domlist : pairMappings) {
                    int countAvail = 0;
                    DomLoc currDomLoc = null;
                    for(DomLoc dl : domlist) {
                        if (dl.isAvailable) {
                            countAvail++;
                            currDomLoc = dl;
                        }
                    }

                    //if there's only one, choose it. Mark overlaps as unavailable
                    if (countAvail == 1) {
                        chooseDomLoc(currDomLoc);
                        chosenDoms++;
                        madeChange = true;
                    }
                }

                //search supergrid for squares that only have one available covering domloc ----------
                for(int c = 0; c < grid[0].length; c++) {
                    for(int r = 0; r < grid.length; r++) {
                        Square currSquare = superGrid[r][c];
                        int countAvail = 0; 
                        DomLoc currDomLoc = null;
                        for(DomLoc dl : currSquare.domlocs) {
                            if (dl != null && dl.isAvailable) {
                                countAvail++;
                                currDomLoc = dl;
                            }
                        }

                        //if there's only one, choose it. Mark overlaps as unavailable
                        if (countAvail == 1) {
                            chooseDomLoc(currDomLoc);
                            chosenDoms++;
                            madeChange = true;
                        }
                    }
                }
            } //END WHILE LOOP ------------------

            // chosenDoms = DCOUNT(n);
            // System.out.println("\n\n chosen \n");
            if(chosenDoms == DCOUNT(n)) {
                // for (DomLoc chosen : setS) {
                //     if (chosen.isChosen) {
                //         System.out.println(chosen);
                //     }
                // }
                return true;
            }
            return false;



    }

    //chooses a given domloc. Marks it as chosen.
    //marks squares on it as covered
    //marks overlapping domlocs as unavailable (includes this one)
    //marks domlocs that have same pair of numbers as unavailable
    public void chooseDomLoc(DomLoc dl) {
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

    //prints pairmappings
    public void printPairMappings(ArrayList<ArrayList<DomLoc>> pairMappings) {
        for(ArrayList<DomLoc> pairs : pairMappings) {
            System.out.println("");
            for(DomLoc dl : pairs) {
                System.out.println(dl);
            }
        }
    }

    //finds the nth triangle number
    public int TRI(int n) {
        return (n * (n + 1) / 2);
    }

    //finds the number of dominoes in a puzzle with numbers 0 through n
    public int DCOUNT(int n) {
        return TRI(n+1);
    }

    //takes an unordered pair of numbers and maps them to a unique index 0 through DCOUNT(n)-1
    public int DINDEX(int n1, int n2) {
        return TRI(Math.max(n1,n2)) + Math.min(n1, n2);
    }

    //an object representing a square in the grid. Contains information relevant to that square
     class Square {

        private int num; //number on the square
        private boolean isCovered; //have we chosen a DomLoc that uses this square yet?
        private int r; //row
        private int c; //column
        private DomLoc[] domlocs; //array of the up to 4 DomLocs that can use this square

        public Square(int num, int r, int c, DomLoc[] domlocs) {
            isCovered = false;
            this.num = num;
            this.r = r;
            this.c = c;
            this.domlocs = domlocs;

        }
    }

    //given a row column, finds all dominos in setS that use that square, returns as an array
    public DomLoc[] findDoms(int r, int c) {
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
     class DomLoc {

        private boolean isAvailable; //is this DomLoc still valid for use?
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

    
        //The grid of numbers 0 through n that represents the puzzle
    // private static int[][] grid;

    //The set of all the Dominoes chosen to be in the solution (for creation)
    // private static ArrayList<DominoLoc> chosenDominoes;

    // //The set of all possible Domino Locations
    // private static ArrayList<DomLoc> setS;

    // //An arraylist of arraylists. Each list is the domlocs that have a certain pair
    // private static ArrayList<ArrayList<DomLoc>> pairMappings;

    // //The grid as a 2d array of more informative square objects. Has info to help solve.
    // private static Square[][] superGrid;

    // //The grid as a 2d array of more informative square objects. Has info to help solve.
    // private static Square[][] superGrid;

     //n is the largest number that appears in the puzzle
    //the smallest number is zero
    public void createPuzzle(int n) {
        int width = n + 2;
        int height = n + 1;
        grid = new int[height][width];

        ArrayList<DominoLoc> possibleLocs = new ArrayList<>();
        chosenDominoes = new ArrayList<>();


        DominoLoc dom;

        //vertical dominoes
        for(int c = 0; c < width; c++) {
            for(int r = 0; r < height-1; r++) {
                dom = new DominoLoc(r, c, false);
                possibleLocs.add(dom); 
            }
        }

        //horizontal dominoes
        for(int r = 0; r < height; r++) {
            for(int c = 0; c < width-1; c++) {
                dom = new DominoLoc(r, c, true);
                possibleLocs.add(dom); 
            }
        }

        //error check
        if(possibleLocs.size() != 2*width*height - width - height) {
            System.out.println("ERROR: Grid not created correctly.");
            return;
        }

        Collections.shuffle(possibleLocs);

        // for (DominoLoc d : possibleLocs) {
        //     System.out.println(d + " ");
        // }

        for (DominoLoc d : possibleLocs) {
            if(d.isHor) {
                if(grid[d.r][d.c] == 0 && grid[d.r][d.c + 1] == 0) {
                    grid[d.r][d.c] = -1;
                    grid[d.r][d.c + 1] = -1;
                    chosenDominoes.add(d);
                }
            } else {
                if(grid[d.r][d.c] == 0 && grid[d.r + 1][d.c] == 0) {
                    grid[d.r][d.c] = -1;
                    grid[d.r+1][d.c] = -1;
                    chosenDominoes.add(d);
                }
            }
            
        }

        Coord coord = findSingleton();

        //singletons!
        while(coord != null) {
            matchSingletons(coord);
            coord = findSingleton();
        }

        ArrayList<Pair> pairs = new ArrayList<>();
        for(int i = 0; i <= n; i++) {
            for (int j = 0; j <= i; j++) {
                pairs.add(new Pair(i, j));
            }
        }

        Collections.shuffle(pairs);

        if(pairs.size() != chosenDominoes.size()) {
            System.out.println("ERROR: pairs and dominoes do not match sizes.");
            return;
        }

        for(int i = 0; i < pairs.size(); i++) {
            grid[chosenDominoes.get(i).r][chosenDominoes.get(i).c] = pairs.get(i).n1;
            grid[chosenDominoes.get(i).r2][chosenDominoes.get(i).c2] = pairs.get(i).n2;
        }

        // printGrid();
    }

    public void matchSingletons(Coord coord) {
        Queue<Coord> queue = new LinkedList<>();

        queue.add(coord);

        while(!queue.isEmpty()) {
            //1: left, 2: up, 3: right, 4:down
            ArrayList<Integer> directions = new ArrayList<>();

            directions.add(1); directions.add(2); directions.add(3); directions.add(4);
            Collections.shuffle(directions);

            Coord currentCoord = queue.remove();


            for (int n = 0; n < directions.size(); n++) {

                int dir = directions.get(n);

                if((currentCoord.r == 0 && dir == 2) || (currentCoord.r == grid.length-1 && dir == 4) 
                    || (currentCoord.c == 0 && dir == 1) || (currentCoord.c == grid[0].length-1 && dir == 3)) {
                    continue;
                }

                Coord targetCoord = null;

                switch(dir) {
                    case 1:
                        targetCoord = new Coord(currentCoord.r, currentCoord.c - 1);
                        break;
                    case 2:
                        targetCoord = new Coord(currentCoord.r -1, currentCoord.c);
                        break;
                    case 3:
                        targetCoord = new Coord(currentCoord.r, currentCoord.c + 1);
                        break;
                    case 4:
                        targetCoord = new Coord(currentCoord.r + 1, currentCoord.c);
                        break;
                    default:
                        System.out.println("ERROR with direction");
                        break;
                }

                //found singleton
                if(grid[targetCoord.r][targetCoord.c] == 0) {
                    ArrayList<Coord> path = new ArrayList<>();

                    //adding singleton
                    path.add(targetCoord);

                    while(currentCoord.r != coord.r || currentCoord.c != coord.c) {
                        path.add(currentCoord);
                        switch(grid[currentCoord.r][currentCoord.c]) {
                            case 1:
                                currentCoord = new Coord(currentCoord.r, currentCoord.c - 1);
                                break;
                            case 2:
                                currentCoord = new Coord(currentCoord.r -1, currentCoord.c);
                                break;
                            case 3:
                                currentCoord = new Coord(currentCoord.r, currentCoord.c + 1);
                                break;
                            case 4:
                                currentCoord = new Coord(currentCoord.r + 1, currentCoord.c);
                                break;
                            default:
                                System.out.println("ERROR with direction");
                                break;
                        }
                    }
                    path.add(coord);

                    for(Coord coordOnPath : path) {
                        for(int i = chosenDominoes.size() - 1; i >= 0; i--) {
                            DominoLoc toCheck = chosenDominoes.get(i);
                            if((toCheck.r == coordOnPath.r && toCheck.c == coordOnPath.c) ||
                                toCheck.r2 == coordOnPath.r && toCheck.c2 == coordOnPath.c) {
                                chosenDominoes.remove(i);
                                break;
                            }
                        }
                    }

                    for(int i = 0; i < path.size(); i+=2) {
                        Coord c1 = path.get(i);
                        Coord c2 = path.get(i+1);

                        DominoLoc newLoc;

                        if(c2.r > c1.r) {
                            newLoc = new DominoLoc(c1.r, c1.c, false);
                        } else if (c2.r < c1.r){
                            newLoc = new DominoLoc(c2.r, c2.c, false);
                        } else if (c2.c < c1.c) {
                            newLoc = new DominoLoc(c2.r, c2.c, true);
                        } else {
                            newLoc = new DominoLoc(c1.r, c1.c, true);
                        }
                        chosenDominoes.add(newLoc);
                    }

                    grid[path.get(0).r][path.get(0).c] = -1;
                    grid[path.get(path.size()-1).r][path.get(path.size()-1).c] = -1;

                    for(int r = 0; r < grid.length; r++) {
                        for(int c = 0; c < grid[r].length; c++) {
                            if(grid[r][c] > 0) {
                                grid[r][c] = -1;
                            }
                        }
                    }

                    return; 
                }
                if(grid[targetCoord.r][targetCoord.c] > 0) {
                    continue;
                }

                grid[targetCoord.r][targetCoord.c] = ((dir % 4) + 1) % 4 + 1;

                Coord jumpCoord = null;
                for(DominoLoc dom: chosenDominoes) {
                    if(dom.r == targetCoord.r && dom.c == targetCoord.c) {
                        if(dom.isHor) {
                            jumpCoord = new Coord(targetCoord.r, targetCoord.c + 1);
                            grid[jumpCoord.r][jumpCoord.c] = 1;
                        } else {
                            jumpCoord = new Coord(targetCoord.r + 1, targetCoord.c);
                            grid[jumpCoord.r][jumpCoord.c] = 2;
                        }
                        break;
                    } else if(dom.r2 == targetCoord.r && dom.c2 == targetCoord.c)  {
                        if(dom.isHor) {
                            jumpCoord = new Coord(targetCoord.r, targetCoord.c - 1);
                            grid[jumpCoord.r][jumpCoord.c] = 3;
                        } else {
                            jumpCoord = new Coord(targetCoord.r - 1, targetCoord.c);
                            grid[jumpCoord.r][jumpCoord.c] = 4;
                        }
                        break;
                    }
                }
                queue.add(jumpCoord);
        }



        }

        return;
    }

    public Coord findSingleton() {
        for(int r = 0; r < grid.length; r++) {
            for(int c = 0; c < grid[r].length; c++) {
                if(grid[r][c] == 0) {
                    return new Coord(r,c);
                }
            }
        }
        return null;
    }

    public void printGrid() {
        for(int r = 0; r < grid.length; r++) {
            System.out.println("");
            for(int c = 0; c < grid[r].length; c++) {
                System.out.print(grid[r][c] + " ");
            }
        }
        System.out.println("");
    }

     class DominoLoc {

        private int r;
        private int c;
        private int r2;
        private int c2;
        private boolean isHor;

        public DominoLoc(int r, int c, boolean isHor) {
            this.r = r;
            this.c = c;
            this.isHor = isHor;

            if(isHor) {
                r2 = r;
                c2 = c + 1;
            } else {
                r2 = r + 1;
                c2 = c;
            }
        }

        public String toString() {
            return "r: " + r + " , c: " + c + " isHor: " + isHor;
        }
    }

     class Coord {

        private int r;
        private int c;

        public Coord(int r, int c) {
            this.r = r;
            this.c = c;
        }

        public String toString() {
            return "r: " + r + " , c: " + c;
        }
    }

         class Pair {

            private int n1;
            private int n2;

            public Pair(int n1, int n2) {
                int ran = (int)(Math.random() * 2);

                if(ran == 0) {
                    this.n1 = n1;
                    this.n2 = n2;
                } else {
                    this.n2 = n1;
                    this.n1 = n2;
                }

            }

            public String toString() {
                return "n1: " + n1 + " , n2: " + n2;
            }
        }

}
    
