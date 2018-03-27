import java.util.ArrayList;

public class ParallelSuperGrid implements Runnable{


    private int thread_id;
    private static ParallelDominosa.Square [][] superGrid;
    private int rowsTodo;

    public void run() {

         for(int r = rowsTodo*thread_id; ((r < rowsTodo*thread_id + rowsTodo) && (r < superGrid.length)); r++) {
            for(int c = 0; c < superGrid[0].length; c++) {
                ParallelDominosa.Square currSquare = superGrid[r][c];
                int countAvail = 0; 
                ParallelDominosa.DomLoc currDomLoc = null;
                for(ParallelDominosa.DomLoc dl : currSquare.domlocs) {
                    if (dl != null && dl.isAvailable) {
                        countAvail++;
                        currDomLoc = dl;
                    }
                }

                //if there's only one, choose it. Mark overlaps as unavailable
                if (countAvail == 1) {
                    ParallelDominosa.chooseDomLoc(currDomLoc);
                    ParallelDominosa.madeChange = true;
                }
            }
        }
    }

    public ParallelSuperGrid(int id, int rowsTodo) {
        this.thread_id = id;
        superGrid = ParallelDominosa.superGrid;
        this.rowsTodo = rowsTodo;
    }

}