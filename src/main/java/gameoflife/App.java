package gameoflife;

import java.util.ArrayList;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;


public class App extends Application {
    
    double zoom = 6.0;
    double minZoom = 0.5;
    double maxZoom = 30.0;

    double panX = 0;
    double panY = 0;

    double lastMouseX;
    double lastMouseY;
    boolean drawValue;

    boolean[][] grid = new boolean[500][500];  //center 250, 250 without mods
    Duration tickrate = Duration.seconds(0.25);  //4 ticks per second
    ArrayList<boolean[][]> history = new ArrayList<>();
    int historyIndex = 0;

    enum Mode {         //used to track states
        DRAWING,
        PLAYING,
        PAUSED
    }

    Mode mode = Mode.DRAWING;  //default mode

    void flashButton(Button button, String normalStyle) {
        button.setStyle(normalStyle + "-fx-background-color: white; -fx-text-fill: black;");

        PauseTransition flash = new PauseTransition(Duration.millis(100));
        flash.setOnFinished(event -> button.setStyle(normalStyle));
        flash.play();

    }

    Timeline timeline;
    boolean isPlaying = false;

    double playbackRate = 1.0;
    double minPlaybackRate = 0.25;
    double maxPlaybackRate = 4.0;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(500, 500); //decouple this later
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.DARKSLATEGRAY);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        Button previousButton = new Button("◀");
        Button pauseButton = new Button("Ⅱ");
        Button nextButton = new Button("▶");
        Button slowerButton = new Button("‹");
        Button fasterButton = new Button("›");


        String normalStyle
                = "-fx-background-color: rgba(20,20,20,0.65);"
                + "-fx-text-fill: white;"
                + "-fx-border-color: white;"
                + "-fx-border-width: 2;"
                + "-fx-background-radius: 0;"
                + "-fx-border-radius: 0;"
                + "-fx-font-family: Monospaced;"
                + "-fx-font-size: 16px;";

        for (Button button : new Button[]{previousButton, pauseButton, nextButton, slowerButton, fasterButton}) {
            button.setPrefSize(36, 36);
            button.setFocusTraversable(false);
            button.setStyle(normalStyle);
}

        HBox navControls = new HBox(6, previousButton, pauseButton, nextButton);
        HBox speedControls = new HBox(6, slowerButton, fasterButton);

        navControls.setAlignment(Pos.CENTER_LEFT);
        speedControls.setAlignment(Pos.CENTER_LEFT);

        VBox controls = new VBox(6, speedControls, navControls);
        controls.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        controls.setAlignment(Pos.BOTTOM_LEFT);

        StackPane pane = new StackPane(canvas, controls);
        StackPane.setAlignment(controls, Pos.BOTTOM_LEFT);
        StackPane.setMargin(controls, new Insets(0, 0, 16, 16));

        Scene scene = new Scene(pane);

        stage.setScene(scene);
        stage.setTitle("Game of Life");
        stage.show();

        scene.setOnKeyPressed(event -> {
            KeyCode key = event.getCode();

            if (key == KeyCode.LEFT || key == KeyCode.J) {
                previousButton.fire();
            }

            if (key == KeyCode.SPACE || key == KeyCode.K) {
                pauseButton.fire();
            }

            if (key == KeyCode.RIGHT || key == KeyCode.L) {
                nextButton.fire();
            }

            if (key == KeyCode.COMMA ) {
                slowerButton.fire();   // <
            }

            if (key == KeyCode.PERIOD ) {
                fasterButton.fire();   // >
            }
            if (key == KeyCode.MINUS) {
                slowerButton.fire();
            }

            if (key == KeyCode.EQUALS) {
                fasterButton.fire();   // + shares the = key
            }
        });

        canvas.setOnScroll(event -> {
            double mouseX = event.getX();       //find where the mouse is 
            double mouseY = event.getY();

            double baseCellSize = Math.min(             //current zoom level (cell size)
                    canvas.getWidth() / grid[0].length,
                    canvas.getHeight() / grid.length
            );

            double oldCellSize = baseCellSize * zoom;         
            double oldGridWidth = grid[0].length * oldCellSize;
            double oldGridHeight = grid.length * oldCellSize;

            double oldCenterX = (canvas.getWidth() - oldGridWidth) / 2;
            double oldCenterY = (canvas.getHeight() - oldGridHeight) / 2;

            double logicalX = (mouseX - oldCenterX - panX) / oldCellSize;       //convert mouse position to grid position
            double logicalY = (mouseY - oldCenterY - panY) / oldCellSize;

            if (event.getDeltaY() > 0) {            //old zoom logic
                zoom *= 1.1;
            } else {
                zoom /= 1.1;
            }

            zoom = Math.max(minZoom, Math.min(maxZoom, zoom));

            double newCellSize = baseCellSize * zoom;       //recalculate grid using new zoom
            double newGridWidth = grid[0].length * newCellSize;
            double newGridHeight = grid.length * newCellSize;

            double newCenterX = (canvas.getWidth() - newGridWidth) / 2;
            double newCenterY = (canvas.getHeight() - newGridHeight) / 2;

            panX = mouseX - newCenterX - logicalX * newCellSize;        //adjust camera to new zoom
            panY = mouseY - newCenterY - logicalY * newCellSize;

            draw(grid, g, canvas);
        });
        canvas.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                lastMouseX = event.getX();
                lastMouseY = event.getY();
                return;
            }

            if (event.getButton() == MouseButton.PRIMARY && mode != Mode.PLAYING) {
                mode = Mode.DRAWING;

                int[] cell = getGridCell(event.getX(), event.getY(), canvas);

                if (cell != null) {
                    int i = cell[0];
                    int j = cell[1];

                    clearFutureHistory(); //for cleanliness
                    drawValue = !grid[i][j];   // start on dead = paint; start on live = erase
                    grid[i][j] = drawValue;

                    draw(grid, g, canvas);
                }
            }
        });

        canvas.setOnMouseDragged(event -> {
            if (event.isSecondaryButtonDown()) {
                panX += event.getX() - lastMouseX;
                panY += event.getY() - lastMouseY;

                lastMouseX = event.getX();
                lastMouseY = event.getY();

                draw(grid, g, canvas);
            }

            if (event.isPrimaryButtonDown() && mode != Mode.PLAYING) {
                int[] cell = getGridCell(event.getX(), event.getY(), canvas);

                if (cell != null) {
                    grid[cell[0]][cell[1]] = drawValue;
                    draw(grid, g, canvas);
                }
            }
        });

        pane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            canvas.setWidth(newWidth.doubleValue());
            draw(grid, g, canvas);
        });

        pane.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            canvas.setHeight(newHeight.doubleValue());
            draw(grid, g, canvas);
        });
        int centerI = grid.length / 2 - 1;
        int centerJ = grid[0].length / 2 - 1;

        grid[centerI][centerJ + 1] = true;
        grid[centerI + 1][centerJ + 2] = true;
        grid[centerI + 2][centerJ] = true;
        grid[centerI + 2][centerJ + 1] = true;
        grid[centerI + 2][centerJ + 2] = true;

        history.add(grid);
        historyIndex = 0;
        history.add(grid);
        historyIndex = 0;

        timeline = new Timeline(
                new KeyFrame(tickrate, event -> {
                    grid = tick(grid);

                    history.add(grid);
                    historyIndex = history.size() - 1;

                    draw(grid, g, canvas);
                })
        );

        timeline.setCycleCount(Animation.INDEFINITE);
        pauseButton.setOnAction(event -> {
            flashButton(pauseButton, normalStyle);

            if (mode == Mode.PLAYING) {
                timeline.pause();
                isPlaying = false;
                mode = Mode.PAUSED;
            } else {
                clearFutureHistory(); //for cleanliness
                timeline.play();
                isPlaying = true;
                mode = Mode.PLAYING;
            }
        });

        slowerButton.setOnAction(event -> {
            flashButton(slowerButton, normalStyle);
            playbackRate = Math.max(minPlaybackRate, playbackRate / 2.0);
            timeline.setRate(playbackRate);
        });

        fasterButton.setOnAction(event -> {
            flashButton(fasterButton, normalStyle);
            playbackRate = Math.min(maxPlaybackRate, playbackRate * 2.0);
            timeline.setRate(playbackRate);
        });

        previousButton.setOnAction(event -> {
            flashButton(previousButton, normalStyle);

            if (!isPlaying && historyIndex > 0) {
                historyIndex--;
                grid = history.get(historyIndex);
                draw(grid, g, canvas);
            }
        });
        nextButton.setOnAction(event -> {
            flashButton(nextButton, normalStyle);

            if (!isPlaying && historyIndex < history.size() - 1) {
                historyIndex++;
                grid = history.get(historyIndex);
                draw(grid, g, canvas);
            }
        });
        draw(grid, g, canvas);
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
        double baseCellSize = Math.min(
                canvas.getWidth() / current[0].length,
                canvas.getHeight() / current.length
        );

        double cellSize = zoom * baseCellSize;


        double gridWidth = current[0].length * cellSize;
        double gridHeight = current.length * cellSize;

        double centerX = (canvas.getWidth() - gridWidth) / 2;
        double centerY = (canvas.getHeight() - gridHeight) / 2;

        g.setFill(Color.DARKSLATEGRAY);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        g.setFill(Color.WHITE);

        for (int i = 0; i < current.length; i++) {
            for (int j = 0; j < current[0].length; j++) {
                if (current[i][j] == true) {
                    double x = centerX + panX + j * cellSize;
                    double y = centerY + panY + i * cellSize;

                    g.fillRect(x, y, cellSize, cellSize);
                }
            }
        }
        g.setStroke(Color.DARKGRAY);        //bounds line 
        g.setLineWidth(2);
        g.strokeRect(centerX + panX, centerY + panY, gridWidth, gridHeight);
    }

    int[] getGridCell(double mouseX, double mouseY, Canvas canvas) {
        double baseCellSize = Math.min(
                canvas.getWidth() / grid[0].length,
                canvas.getHeight() / grid.length
        );

        double cellSize = baseCellSize * zoom;

        double gridWidth = grid[0].length * cellSize;
        double gridHeight = grid.length * cellSize;

        double centerX = (canvas.getWidth() - gridWidth) / 2;
        double centerY = (canvas.getHeight() - gridHeight) / 2;

        int j = (int) ((mouseX - centerX - panX) / cellSize);
        int i = (int) ((mouseY - centerY - panY) / cellSize);

        if (i < 0 || i >= grid.length || j < 0 || j >= grid[0].length) {
            return null;
        }

        return new int[]{i, j};
    }

    void clearFutureHistory() {
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}