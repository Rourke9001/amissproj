package amiss.domain.board;

/**
 * Class to generate the board object
 * @author The Rourke
 */
public class TwoDGrid {

    private char grid[][] = new char[4][4];

    /**
     * Generate the board
     */
    public TwoDGrid() {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if ((r == 1 || r == 2) && (c == 1 || c == 2)) {
                    grid[r][c] = ' ';
                } else {
                    grid[r][c] = '*';
                }
            }
        }
        int row = 0, col = 0;
    }

    /**
     * returns the users xy position
     * @param r x coordinate
     * @param c y coordinate
     * @return the users coordinates
     */
    public char getBlock(int r, int c) {
        return grid[r][c];
    }

    @Override
    public String toString() {
        String temp = "";
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                temp = temp + grid[r][c];

            }
            temp = temp + "\n";
        }
        return temp;
    }

}
