package gameoflife;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    boolean[][] grid = new boolean[500][500];
    Duration tickrate = Duration.seconds(0.25);  //4 ticks per second

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(500, 500);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.DARKSLATEGRAY);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        stage.setScene(new Scene(new StackPane(canvas)));
        stage.setTitle("Game of Life");
        stage.show();

        int centerI = grid.length / 2 - 1;
        int centerJ = grid[0].length / 2 - 1;
        grid[centerI][centerJ + 1] = true;         // row 0: false true false
        grid[centerI + 1][centerJ + 2] = true;     // row 1: false false true
        grid[centerI + 2][centerJ] = true;         // row 2: true true true
        grid[centerI + 2][centerJ + 1] = true;
        grid[centerI + 2][centerJ + 2] = true;

        Timeline timeline = new Timeline(
                new KeyFrame(tickrate, event -> {
                    grid = tick(grid);
                    draw(grid, g, canvas);
                })
        );

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

    }

    int getNeighbors(boolean[][] current, int i, int j) {  //Helper function, returns # neighbors of input cell
        int neighbors = 0;

        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if (di == 0 && dj == 0) { continue; }

                int ni = i + di;
                int nj = j + dj;

                if (ni >= 0 && ni < current.length && nj >= 0 && nj < current[0].length && current[ni][nj]) {
                    neighbors++;
                }
            }
        }

        return neighbors;
    }

    boolean[][] tick(boolean[][] current) {
        boolean[][] newGrid = new boolean[current.length][current[0].length];

        for (int i = 0; i < current.length; i++) {
            for (int j = 0; j < current[0].length; j++) {

                int neighbors = getNeighbors(current, i, j);

                if (current[i][j] == true && neighbors < 2) { newGrid[i][j] = false; }  // Live cell with fewer than 2 neighbors dies
                if (current[i][j] == true && (neighbors == 2 || neighbors == 3)) { newGrid[i][j] = true; } // Live cell with 2 or 3 neighbors survives
                if (current[i][j] == true && neighbors > 3) { newGrid[i][j] = false; } // Live cell with more than 3 neighbors dies
                if (current[i][j] == false && neighbors == 3) { newGrid[i][j] = true; } // Dead cell with exactly 3 neighbors becomes alive
            }
        }

        return newGrid;
    }

    void draw(boolean[][] current, GraphicsContext g, Canvas canvas) {
        double cellWidth = canvas.getWidth() / current[0].length;
        double cellHeight = canvas.getHeight() / current.length;

        // Clear previous frame
        g.setFill(Color.DARKSLATEGRAY);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw live cells
        g.setFill(Color.WHITE);

        for (int i = 0; i < current.length; i++) {
            for (int j = 0; j < current[0].length; j++) {
                if (current[i][j] == true) {
                    g.fillRect(j * cellWidth, i * cellHeight, cellWidth, cellHeight);
                }
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}