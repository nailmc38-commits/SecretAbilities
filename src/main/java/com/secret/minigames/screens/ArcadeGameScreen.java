package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Random;

public final class ArcadeGameScreen extends Screen {
    public enum Game {
        SNAKE("Snake"), PONG("Pong"), BREAKOUT("Breakout"), ASTEROIDS("Asteroids"),
        INVADERS("Space Invaders"), FLAPPY("Flappy Block"), GAME_2048("2048"),
        MINESWEEPER("Minesweeper"), MEMORY("Memory Match"), SOKOBAN("Sokoban"),
        FROGGER("Frogger"), PLATFORMER("Platformer"), RACING("Neon Racing"),
        TOWER_DEFENSE("Tower Defense"), DUNGEON("Dungeon Crawler");

        public final String title;
        Game(String title) { this.title = title; }
    }

    private final Screen parent;
    private final Game game;
    private final Random random = new Random();
    private long lastNanos;
    private int score;
    private int lives;
    private boolean gameOver;
    private boolean won;

    // Snake
    private final int[] snakeX = new int[400], snakeY = new int[400];
    private int snakeLen, snakeDx, snakeDy, nextDx, nextDy, foodX, foodY;
    private double snakeTimer;

    // Pong / Breakout / Flappy / Racing shared numeric state
    private double p1Y, aiY, ballX, ballY, ballVX, ballVY;
    private int playerPoints, aiPoints;
    private final boolean[][] bricks = new boolean[5][10];
    private double paddleX;
    private double birdY, birdV, pipeX, pipeGap;
    private double raceX, raceSpeed, raceDistance;
    private final double[] raceObsX = new double[8], raceObsY = new double[8];

    // Asteroids
    private double shipX, shipY, shipVX, shipVY, shipAngle;
    private final double[] astX = new double[14], astY = new double[14], astVX = new double[14], astVY = new double[14];
    private final boolean[] astAlive = new boolean[14];
    private final double[] shotX = new double[24], shotY = new double[24], shotVX = new double[24], shotVY = new double[24], shotLife = new double[24];

    // Invaders
    private final boolean[][] invaders = new boolean[5][9];
    private double invX, invY, invDir, invMoveTimer, invPlayerX;
    private double pShotX, pShotY, eShotX, eShotY;

    // 2048
    private final int[][] tiles = new int[4][4];

    // Minesweeper
    private static final int MINE_W = 10, MINE_H = 8;
    private final boolean[][] mines = new boolean[MINE_H][MINE_W];
    private final boolean[][] revealed = new boolean[MINE_H][MINE_W];
    private final boolean[][] flagged = new boolean[MINE_H][MINE_W];
    private int mineCursorX, mineCursorY;

    // Memory
    private final int[] cards = new int[16];
    private final boolean[] matched = new boolean[16];
    private int memoryCursor, firstCard, secondCard;
    private double memoryHideTimer;

    // Sokoban
    private boolean[][] sokoWalls, sokoGoals, sokoBoxes;
    private int sokoPX, sokoPY;

    // Frogger
    private int frogX, frogY;
    private final double[] carX = new double[12];
    private final int[] carLane = new int[12];
    private final double[] carSpeed = new double[12];

    // Platformer
    private final boolean[][] platform = new boolean[80][15];
    private final boolean[][] platformCoin = new boolean[80][15];
    private double platX, platY, platVX, platVY;

    // Tower defense
    private final boolean[][] towers = new boolean[3][5];
    private final double[] enemyX = new double[24], enemyHp = new double[24];
    private final int[] enemyLane = new int[24];
    private final boolean[] enemyActive = new boolean[24];
    private int towerCursorX, towerCursorY, gold, baseHp;
    private double spawnTimer;

    // Dungeon
    private static final String[] DUNGEON_MAP = {
            "################", "#S...#.....#..E#", "#.##.#.###.#.#.#", "#....#...#...#.#",
            "####.###.#.###.#", "#....#...#.....#", "#.####.#####.#.#", "#......#.....#.#",
            "#.####.#.###.#.#", "#......#...#...#", "#..*.......*.*.#", "################"
    };
    private int dungeonX, dungeonY, dungeonHp, crystals;
    private final int[] dungeonEX = new int[6], dungeonEY = new int[6];
    private final boolean[] dungeonEnemyAlive = new boolean[6];
    private final boolean[][] crystalTaken = new boolean[DUNGEON_MAP.length][DUNGEON_MAP[0].length()];

    public ArcadeGameScreen(Screen parent, Game game) {
        super(Text.literal(game.title));
        this.parent = parent;
        this.game = game;
        resetGame();
    }

    @Override
    protected void init() {
        lastNanos = System.nanoTime();
    }

    private void resetGame() {
        score = 0;
        lives = 3;
        gameOver = false;
        won = false;
        switch (game) {
            case SNAKE -> resetSnake();
            case PONG -> resetPong();
            case BREAKOUT -> resetBreakout();
            case ASTEROIDS -> resetAsteroids();
            case INVADERS -> resetInvaders();
            case FLAPPY -> resetFlappy();
            case GAME_2048 -> reset2048();
            case MINESWEEPER -> resetMines();
            case MEMORY -> resetMemory();
            case SOKOBAN -> resetSokoban();
            case FROGGER -> resetFrogger();
            case PLATFORMER -> resetPlatformer();
            case RACING -> resetRacing();
            case TOWER_DEFENSE -> resetTowerDefense();
            case DUNGEON -> resetDungeon();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        double dt = Math.min(0.05, Math.max(0.001, (now - lastNanos) / 1_000_000_000.0));
        lastNanos = now;
        if (!gameOver && !won) update(dt);

        ctx.fill(0, 0, width, height, 0xFF0D1118);
        ctx.fill(0, 0, width, 4, 0xFF51E6D7);
        drawGame(ctx);
        drawHeader(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void update(double dt) {
        switch (game) {
            case SNAKE -> updateSnake(dt);
            case PONG -> updatePong(dt);
            case BREAKOUT -> updateBreakout(dt);
            case ASTEROIDS -> updateAsteroids(dt);
            case INVADERS -> updateInvaders(dt);
            case FLAPPY -> updateFlappy(dt);
            case GAME_2048, MINESWEEPER, SOKOBAN, DUNGEON -> { }
            case MEMORY -> updateMemory(dt);
            case FROGGER -> updateFrogger(dt);
            case PLATFORMER -> updatePlatformer(dt);
            case RACING -> updateRacing(dt);
            case TOWER_DEFENSE -> updateTowerDefense(dt);
        }
    }

    private void drawGame(DrawContext ctx) {
        switch (game) {
            case SNAKE -> drawSnake(ctx);
            case PONG -> drawPong(ctx);
            case BREAKOUT -> drawBreakout(ctx);
            case ASTEROIDS -> drawAsteroids(ctx);
            case INVADERS -> drawInvaders(ctx);
            case FLAPPY -> drawFlappy(ctx);
            case GAME_2048 -> draw2048(ctx);
            case MINESWEEPER -> drawMines(ctx);
            case MEMORY -> drawMemory(ctx);
            case SOKOBAN -> drawSokoban(ctx);
            case FROGGER -> drawFrogger(ctx);
            case PLATFORMER -> drawPlatformer(ctx);
            case RACING -> drawRacing(ctx);
            case TOWER_DEFENSE -> drawTowerDefense(ctx);
            case DUNGEON -> drawDungeon(ctx);
        }
    }

    private void drawHeader(DrawContext ctx) {
        ctx.fill(0, 4, width, 44, 0xEE151B25);
        ctx.drawTextWithShadow(textRenderer, Text.literal(game.title.toUpperCase()).formatted(Formatting.AQUA, Formatting.BOLD), 12, 13, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Score: " + score + "  •  R restart  •  F9/ESC hub").formatted(Formatting.GRAY), 12, 28, 0xFFFFFF);
        String help = controls();
        ctx.drawTextWithShadow(textRenderer, Text.literal(help).formatted(Formatting.WHITE), Math.max(12, width - textRenderer.getWidth(help) - 12), 18, 0xFFFFFF);
        if (gameOver || won) {
            String msg = won ? "YOU WIN!" : "GAME OVER";
            int boxW = 210;
            ctx.fill(width / 2 - boxW / 2, height / 2 - 36, width / 2 + boxW / 2, height / 2 + 36, 0xE8000000);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(msg).formatted(won ? Formatting.GREEN : Formatting.RED, Formatting.BOLD), width / 2, height / 2 - 12, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Press R to play again"), width / 2, height / 2 + 8, 0xFFFFFF);
        }
    }

    private String controls() {
        return switch (game) {
            case SNAKE -> "Arrows/WASD";
            case PONG -> "W / S";
            case BREAKOUT -> "A / D";
            case ASTEROIDS -> "A/D rotate • W thrust • Space shoot";
            case INVADERS -> "A/D move • Space shoot";
            case FLAPPY -> "Space flap";
            case GAME_2048 -> "Arrow keys";
            case MINESWEEPER -> "Arrows • Enter reveal • F flag";
            case MEMORY -> "Arrows • Enter flip";
            case SOKOBAN -> "Arrows move/push";
            case FROGGER -> "Arrow keys";
            case PLATFORMER -> "A/D move • Space jump";
            case RACING -> "A/D steer • W/S speed";
            case TOWER_DEFENSE -> "Arrows select • Enter build (25 gold)";
            case DUNGEON -> "Arrows move • E clear adjacent enemy";
        };
    }

    // ---------- Snake ----------
    private void resetSnake() {
        snakeLen = 5;
        for (int i = 0; i < snakeLen; i++) { snakeX[i] = 10 - i; snakeY[i] = 8; }
        snakeDx = nextDx = 1;
        snakeDy = nextDy = 0;
        snakeTimer = 0;
        spawnFood();
    }

    private void spawnFood() {
        do { foodX = random.nextInt(24); foodY = random.nextInt(16); } while (snakeAt(foodX, foodY));
    }

    private boolean snakeAt(int x, int y) {
        for (int i = 0; i < snakeLen; i++) if (snakeX[i] == x && snakeY[i] == y) return true;
        return false;
    }

    private void updateSnake(double dt) {
        snakeTimer += dt;
        if (snakeTimer < Math.max(0.055, 0.14 - score * 0.00035)) return;
        snakeTimer = 0;
        snakeDx = nextDx; snakeDy = nextDy;
        int nx = snakeX[0] + snakeDx, ny = snakeY[0] + snakeDy;
        if (nx < 0 || nx >= 24 || ny < 0 || ny >= 16 || snakeAt(nx, ny)) { gameOver = true; return; }
        for (int i = Math.min(snakeLen, snakeX.length - 1); i > 0; i--) { snakeX[i] = snakeX[i - 1]; snakeY[i] = snakeY[i - 1]; }
        snakeX[0] = nx; snakeY[0] = ny;
        if (nx == foodX && ny == foodY) { snakeLen++; score += 10; spawnFood(); }
    }

    private void drawSnake(DrawContext ctx) {
        int cell = Math.max(12, Math.min((width - 40) / 24, (height - 78) / 16));
        int ox = (width - cell * 24) / 2, oy = 54 + (height - 54 - cell * 16) / 2;
        ctx.fill(ox - 2, oy - 2, ox + cell * 24 + 2, oy + cell * 16 + 2, 0xFF29333E);
        ctx.fill(ox, oy, ox + cell * 24, oy + cell * 16, 0xFF101820);
        ctx.fill(ox + foodX * cell + 2, oy + foodY * cell + 2, ox + (foodX + 1) * cell - 2, oy + (foodY + 1) * cell - 2, 0xFFFF5B5B);
        for (int i = 0; i < snakeLen; i++) {
            int c = i == 0 ? 0xFF8DFF8A : 0xFF4CCB66;
            ctx.fill(ox + snakeX[i] * cell + 1, oy + snakeY[i] * cell + 1, ox + (snakeX[i] + 1) * cell - 1, oy + (snakeY[i] + 1) * cell - 1, c);
        }
    }

    // ---------- Pong ----------
    private void resetPong() {
        p1Y = aiY = 0.5; playerPoints = aiPoints = 0; resetPongBall(1);
    }
    private void resetPongBall(int dir) { ballX = 0.5; ballY = 0.5; ballVX = 0.47 * dir; ballVY = (random.nextDouble() - 0.5) * 0.55; }
    private void updatePong(double dt) {
        long win = window();
        if (win != 0) {
            if (down(win, GLFW.GLFW_KEY_W)) p1Y -= dt * 0.75;
            if (down(win, GLFW.GLFW_KEY_S)) p1Y += dt * 0.75;
        }
        p1Y = clamp(p1Y, 0.1, 0.9);
        aiY += Math.signum(ballY - aiY) * dt * 0.52;
        aiY = clamp(aiY, 0.1, 0.9);
        ballX += ballVX * dt; ballY += ballVY * dt;
        if (ballY < 0.04 || ballY > 0.96) { ballVY *= -1; ballY = clamp(ballY, 0.04, 0.96); }
        if (ballX < 0.08 && ballVX < 0 && Math.abs(ballY - p1Y) < 0.13) { ballVX = Math.abs(ballVX) * 1.04; ballVY += (ballY - p1Y) * 1.2; }
        if (ballX > 0.92 && ballVX > 0 && Math.abs(ballY - aiY) < 0.13) { ballVX = -Math.abs(ballVX) * 1.04; ballVY += (ballY - aiY) * 1.2; }
        if (ballX < -0.02) { aiPoints++; resetPongBall(1); }
        if (ballX > 1.02) { playerPoints++; score = playerPoints; resetPongBall(-1); }
        if (playerPoints >= 7) won = true;
        if (aiPoints >= 7) gameOver = true;
    }
    private void drawPong(DrawContext ctx) {
        int l = 30, t = 55, r = width - 30, b = height - 20;
        ctx.fill(l, t, r, b, 0xFF101820);
        for (int y = t; y < b; y += 18) ctx.fill(width / 2 - 1, y, width / 2 + 1, Math.min(b, y + 10), 0xFF52606D);
        int py = t + (int)(p1Y * (b - t)), ay = t + (int)(aiY * (b - t));
        ctx.fill(l + 10, py - 32, l + 18, py + 32, 0xFF5CE1E6);
        ctx.fill(r - 18, ay - 32, r - 10, ay + 32, 0xFFFFA94D);
        int bx = l + (int)(ballX * (r - l)), by = t + (int)(ballY * (b - t));
        ctx.fill(bx - 5, by - 5, bx + 5, by + 5, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(playerPoints + "     " + aiPoints), width / 2, 60, 0xFFFFFF);
    }

    // ---------- Breakout ----------
    private void resetBreakout() {
        for (boolean[] row : bricks) Arrays.fill(row, true);
        paddleX = 0.5; ballX = 0.5; ballY = 0.78; ballVX = 0.34; ballVY = -0.45; lives = 3;
    }
    private void updateBreakout(double dt) {
        long win = window();
        if (win != 0) {
            if (down(win, GLFW.GLFW_KEY_A) || down(win, GLFW.GLFW_KEY_LEFT)) paddleX -= dt * 0.8;
            if (down(win, GLFW.GLFW_KEY_D) || down(win, GLFW.GLFW_KEY_RIGHT)) paddleX += dt * 0.8;
        }
        paddleX = clamp(paddleX, 0.12, 0.88);
        ballX += ballVX * dt; ballY += ballVY * dt;
        if (ballX < 0.03 || ballX > 0.97) { ballVX *= -1; ballX = clamp(ballX, 0.03, 0.97); }
        if (ballY < 0.04) { ballVY = Math.abs(ballVY); ballY = 0.04; }
        if (ballY > 0.87 && ballY < 0.93 && ballVY > 0 && Math.abs(ballX - paddleX) < 0.13) {
            ballVY = -Math.abs(ballVY); ballVX += (ballX - paddleX) * 1.2;
        }
        if (ballY >= 1.03) {
            lives--; if (lives <= 0) gameOver = true;
            ballX = paddleX; ballY = 0.78; ballVX = 0.34 * (random.nextBoolean() ? 1 : -1); ballVY = -0.45;
        }
        if (ballY >= 0.08 && ballY < 0.40) {
            int row = (int)((ballY - 0.08) / 0.064), col = (int)(ballX * 10);
            if (row >= 0 && row < 5 && col >= 0 && col < 10 && bricks[row][col]) {
                bricks[row][col] = false; ballVY *= -1; score += 10;
            }
        }
        boolean any = false; for (boolean[] row : bricks) for (boolean v : row) any |= v;
        if (!any) won = true;
    }
    private void drawBreakout(DrawContext ctx) {
        int l=35,t=54,r=width-35,b=height-20; ctx.fill(l,t,r,b,0xFF0C1420);
        int bw=(r-l)/10, bh=Math.max(12,(b-t)/18);
        int[] colors={0xFFFF5C5C,0xFFFFA64C,0xFFFFDF5C,0xFF62D66F,0xFF57B8FF};
        for(int y=0;y<5;y++) for(int x=0;x<10;x++) if(bricks[y][x]) ctx.fill(l+x*bw+2,t+12+y*bh,l+(x+1)*bw-2,t+12+(y+1)*bh-2,colors[y]);
        int px=l+(int)(paddleX*(r-l)), by=t+(int)(ballY*(b-t)), bx=l+(int)(ballX*(r-l));
        ctx.fill(px-45,b-24,px+45,b-17,0xFF6DE7F2); ctx.fill(bx-5,by-5,bx+5,by+5,0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer,Text.literal("Lives: "+lives),l+6,b-14,0xFFFFFF);
    }

    // ---------- Asteroids ----------
    private void resetAsteroids() {
        shipX=shipY=0.5; shipVX=shipVY=0; shipAngle=-Math.PI/2; lives=3;
        Arrays.fill(astAlive,true); Arrays.fill(shotLife,0);
        for(int i=0;i<astX.length;i++) spawnAsteroid(i);
    }
    private void spawnAsteroid(int i) {
        double edge=random.nextDouble(); astX[i]=edge<0.5?random.nextDouble(): (random.nextBoolean()?0.03:0.97);
        astY[i]=edge<0.5?(random.nextBoolean()?0.08:0.92):random.nextDouble();
        double a=random.nextDouble()*Math.PI*2, sp=0.05+random.nextDouble()*0.10;
        astVX[i]=Math.cos(a)*sp; astVY[i]=Math.sin(a)*sp; astAlive[i]=true;
    }
    private void fireAsteroidShot(){
        for(int i=0;i<shotLife.length;i++) if(shotLife[i]<=0){ shotX[i]=shipX;shotY[i]=shipY;shotVX[i]=Math.cos(shipAngle)*0.85;shotVY[i]=Math.sin(shipAngle)*0.85;shotLife[i]=1.2;return; }
    }
    private void updateAsteroids(double dt){
        long win=window(); if(win!=0){ if(down(win,GLFW.GLFW_KEY_A))shipAngle-=2.8*dt;if(down(win,GLFW.GLFW_KEY_D))shipAngle+=2.8*dt;if(down(win,GLFW.GLFW_KEY_W)){shipVX+=Math.cos(shipAngle)*0.32*dt;shipVY+=Math.sin(shipAngle)*0.32*dt;} }
        shipX=wrap(shipX+shipVX*dt);shipY=wrap(shipY+shipVY*dt);shipVX*=Math.pow(0.985,dt*60);shipVY*=Math.pow(0.985,dt*60);
        for(int i=0;i<astX.length;i++)if(astAlive[i]){astX[i]=wrap(astX[i]+astVX[i]*dt);astY[i]=wrap(astY[i]+astVY[i]*dt);if(dist(shipX,shipY,astX[i],astY[i])<0.035){lives--;spawnAsteroid(i);shipX=shipY=.5;shipVX=shipVY=0;if(lives<=0)gameOver=true;}}
        for(int s=0;s<shotLife.length;s++)if(shotLife[s]>0){shotLife[s]-=dt;shotX[s]=wrap(shotX[s]+shotVX[s]*dt);shotY[s]=wrap(shotY[s]+shotVY[s]*dt);for(int i=0;i<astX.length;i++)if(astAlive[i]&&dist(shotX[s],shotY[s],astX[i],astY[i])<.035){shotLife[s]=0;score+=10;spawnAsteroid(i);break;}}
        if(score>=250)won=true;
    }
    private void drawAsteroids(DrawContext ctx){
        int l=22,t=50,r=width-22,b=height-18;ctx.fill(l,t,r,b,0xFF050811);
        for(int i=0;i<astX.length;i++)if(astAlive[i]){int x=l+(int)(astX[i]*(r-l)),y=t+(int)(astY[i]*(b-t));ctx.fill(x-7,y-7,x+7,y+7,0xFF89939E);ctx.fill(x-3,y-3,x+4,y+4,0xFF59616A);}
        for(int i=0;i<shotLife.length;i++)if(shotLife[i]>0){int x=l+(int)(shotX[i]*(r-l)),y=t+(int)(shotY[i]*(b-t));ctx.fill(x-2,y-2,x+2,y+2,0xFFFFFF88);}
        int x=l+(int)(shipX*(r-l)),y=t+(int)(shipY*(b-t));ctx.fill(x-6,y-6,x+7,y+7,0xFF52E8E0);ctx.fill(x-2,y-2,x+3,y+3,0xFF0A1218);
        ctx.drawTextWithShadow(textRenderer,Text.literal("Lives: "+lives+" • Clear 250 points"),l+6,t+6,0xFFFFFF);
    }

    // ---------- Space Invaders ----------
    private void resetInvaders(){ for(boolean[] row:invaders)Arrays.fill(row,true);invX=.18;invY=.15;invDir=1;invMoveTimer=0;invPlayerX=.5;pShotY=-1;eShotY=-1;lives=3; }
    private void updateInvaders(double dt){
        long win=window();if(win!=0){if(down(win,GLFW.GLFW_KEY_A)||down(win,GLFW.GLFW_KEY_LEFT))invPlayerX-=dt*.65;if(down(win,GLFW.GLFW_KEY_D)||down(win,GLFW.GLFW_KEY_RIGHT))invPlayerX+=dt*.65;}invPlayerX=clamp(invPlayerX,.04,.96);
        invMoveTimer+=dt;if(invMoveTimer>.42){invMoveTimer=0;invX+=invDir*.025;if(invX<.07||invX>.33){invDir*=-1;invY+=.035;}}
        if(pShotY>=0){pShotY-=dt*.85;for(int y=0;y<5;y++)for(int x=0;x<9;x++)if(invaders[y][x]){double ix=invX+x*.067,iy=invY+y*.065;if(Math.abs(pShotX-ix)<.026&&Math.abs(pShotY-iy)<.03){invaders[y][x]=false;pShotY=-1;score+=10;}}}
        if(eShotY<0&&random.nextDouble()<dt*.9){int tries=40;while(tries-->0){int x=random.nextInt(9),y=random.nextInt(5);if(invaders[y][x]){eShotX=invX+x*.067;eShotY=invY+y*.065;break;}}}
        if(eShotY>=0){eShotY+=dt*.42;if(eShotY>.88&&Math.abs(eShotX-invPlayerX)<.04){lives--;eShotY=-1;if(lives<=0)gameOver=true;}else if(eShotY>1)eShotY=-1;}
        boolean any=false;for(boolean[] row:invaders)for(boolean a:row)any|=a;if(!any)won=true;if(invY>.63)gameOver=true;
    }
    private void drawInvaders(DrawContext ctx){int l=25,t=52,r=width-25,b=height-18;ctx.fill(l,t,r,b,0xFF06101A);for(int y=0;y<5;y++)for(int x=0;x<9;x++)if(invaders[y][x]){int ix=l+(int)((invX+x*.067)*(r-l)),iy=t+(int)((invY+y*.065)*(b-t));ctx.fill(ix-8,iy-5,ix+8,iy+5,0xFF69F078);ctx.fill(ix-4,iy-8,ix+4,iy+8,0xFF69F078);}int px=l+(int)(invPlayerX*(r-l));ctx.fill(px-14,b-23,px+14,b-14,0xFF62C9FF);ctx.fill(px-3,b-29,px+3,b-14,0xFF62C9FF);if(pShotY>=0){int x=l+(int)(pShotX*(r-l)),y=t+(int)(pShotY*(b-t));ctx.fill(x-1,y-6,x+2,y+6,0xFFFFFFFF);}if(eShotY>=0){int x=l+(int)(eShotX*(r-l)),y=t+(int)(eShotY*(b-t));ctx.fill(x-2,y-5,x+2,y+5,0xFFFF6D6D);}ctx.drawTextWithShadow(textRenderer,Text.literal("Lives: "+lives),l+5,t+5,0xFFFFFF);}

    // ---------- Flappy ----------
    private void resetFlappy(){birdY=.5;birdV=0;pipeX=1.05;pipeGap=.5;score=0;}
    private void flap(){if(!gameOver){birdV=-.58;}}
    private void updateFlappy(double dt){birdV+=1.45*dt;birdY+=birdV*dt;pipeX-=.38*dt;if(pipeX<-.1){pipeX=1.1;pipeGap=.28+random.nextDouble()*.44;score++;}boolean inPipe=pipeX>.37&&pipeX<.53;if(birdY<.03||birdY>.97||(inPipe&&Math.abs(birdY-pipeGap)>.17))gameOver=true;}
    private void drawFlappy(DrawContext ctx){int l=22,t=52,r=width-22,b=height-18;ctx.fill(l,t,r,b,0xFF6CBCE8);int birdX=l+(int)(.42*(r-l)),birdYY=t+(int)(birdY*(b-t));ctx.fill(birdX-10,birdYY-8,birdX+10,birdYY+8,0xFFFFD64A);int px=l+(int)(pipeX*(r-l)),gap=t+(int)(pipeGap*(b-t)),gapH=(int)(.17*(b-t));ctx.fill(px-18,t,px+18,gap-gapH,0xFF4ABA58);ctx.fill(px-18,gap+gapH,px+18,b,0xFF4ABA58);}

    // ---------- 2048 ----------
    private void reset2048(){for(int[] row:tiles)Arrays.fill(row,0);addTile();addTile();}
    private void addTile(){int empty=0;for(int[] row:tiles)for(int v:row)if(v==0)empty++;if(empty==0)return;int pick=random.nextInt(empty);for(int y=0;y<4;y++)for(int x=0;x<4;x++)if(tiles[y][x]==0&&pick--==0){tiles[y][x]=random.nextInt(10)==0?4:2;return;}}
    private void move2048(int dx,int dy){if(gameOver)return;boolean changed=false;for(int line=0;line<4;line++){int[] old=new int[4];for(int i=0;i<4;i++){int x=dx>0?3-i:dx<0?i:line;int y=dy>0?3-i:dy<0?i:line;old[i]=tiles[y][x];}int[] compact=new int[4];int n=0;for(int v:old)if(v!=0)compact[n++]=v;for(int i=0;i<n-1;i++)if(compact[i]==compact[i+1]){compact[i]*=2;score+=compact[i];if(compact[i]>=2048)won=true;for(int j=i+1;j<n-1;j++)compact[j]=compact[j+1];compact[--n]=0;}for(int i=0;i<4;i++){int x=dx>0?3-i:dx<0?i:line;int y=dy>0?3-i:dy<0?i:line;if(tiles[y][x]!=compact[i])changed=true;tiles[y][x]=compact[i];}}if(changed)addTile();if(!has2048Move())gameOver=true;}
    private boolean has2048Move(){for(int y=0;y<4;y++)for(int x=0;x<4;x++){if(tiles[y][x]==0)return true;if(x<3&&tiles[y][x]==tiles[y][x+1])return true;if(y<3&&tiles[y][x]==tiles[y+1][x])return true;}return false;}
    private void draw2048(DrawContext ctx){int size=Math.min(width-70,height-90),cell=size/4,ox=(width-cell*4)/2,oy=54+(height-54-cell*4)/2;ctx.fill(ox-6,oy-6,ox+cell*4+6,oy+cell*4+6,0xFF6D6257);for(int y=0;y<4;y++)for(int x=0;x<4;x++){int v=tiles[y][x],c=tileColor(v);ctx.fill(ox+x*cell+4,oy+y*cell+4,ox+(x+1)*cell-4,oy+(y+1)*cell-4,c);if(v>0)ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(Integer.toString(v)).formatted(Formatting.BOLD),ox+x*cell+cell/2,oy+y*cell+cell/2-4,0xFFFFFFFF);}}
    private int tileColor(int v){return switch(v){case 0->0xFF8B8177;case 2->0xFFEDE3D7;case 4->0xFFEAD9B7;case 8->0xFFF3B76B;case 16->0xFFF28D55;case 32->0xFFEF6F50;case 64->0xFFE84D38;case 128->0xFFE6C353;case 256->0xFFDDB943;case 512->0xFFD5AE32;default->0xFFB889E8;};}

    // ---------- Minesweeper ----------
    private void resetMines(){for(boolean[] r:mines)Arrays.fill(r,false);for(boolean[] r:revealed)Arrays.fill(r,false);for(boolean[] r:flagged)Arrays.fill(r,false);int left=12;while(left>0){int x=random.nextInt(MINE_W),y=random.nextInt(MINE_H);if(!mines[y][x]){mines[y][x]=true;left--;}}mineCursorX=mineCursorY=0;}
    private int adjacentMines(int x,int y){int n=0;for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){int xx=x+ox,yy=y+oy;if(xx>=0&&xx<MINE_W&&yy>=0&&yy<MINE_H&&mines[yy][xx])n++;}return n;}
    private void revealMine(int x,int y){if(x<0||x>=MINE_W||y<0||y>=MINE_H||revealed[y][x]||flagged[y][x])return;revealed[y][x]=true;if(mines[y][x]){gameOver=true;return;}score++;if(adjacentMines(x,y)==0)for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++)if(ox!=0||oy!=0)revealMine(x+ox,y+oy);if(score>=MINE_W*MINE_H-12)won=true;}
    private void drawMines(DrawContext ctx){int cell=Math.max(20,Math.min((width-60)/MINE_W,(height-90)/MINE_H)),ox=(width-cell*MINE_W)/2,oy=55+(height-55-cell*MINE_H)/2;for(int y=0;y<MINE_H;y++)for(int x=0;x<MINE_W;x++){int l=ox+x*cell,t=oy+y*cell;int c=revealed[y][x]?0xFF29343E:0xFF566675;if(x==mineCursorX&&y==mineCursorY)c=0xFF7F9FB8;ctx.fill(l+1,t+1,l+cell-1,t+cell-1,c);if(flagged[y][x]&&!revealed[y][x])ctx.drawCenteredTextWithShadow(textRenderer,Text.literal("F"),l+cell/2,t+cell/2-4,0xFFFFD65C);if(revealed[y][x]){if(mines[y][x])ctx.drawCenteredTextWithShadow(textRenderer,Text.literal("X"),l+cell/2,t+cell/2-4,0xFFFF5C5C);else{int n=adjacentMines(x,y);if(n>0)ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(Integer.toString(n)),l+cell/2,t+cell/2-4,0xFFFFFFFF);}}}}

    // ---------- Memory ----------
    private void resetMemory(){for(int i=0;i<16;i++)cards[i]=i/2;for(int i=15;i>0;i--){int j=random.nextInt(i+1),tmp=cards[i];cards[i]=cards[j];cards[j]=tmp;}Arrays.fill(matched,false);memoryCursor=0;firstCard=secondCard=-1;memoryHideTimer=0;}
    private void flipMemory(){if(secondCard>=0||matched[memoryCursor]||memoryCursor==firstCard)return;if(firstCard<0)firstCard=memoryCursor;else{secondCard=memoryCursor;if(cards[firstCard]==cards[secondCard]){matched[firstCard]=matched[secondCard]=true;score+=10;firstCard=secondCard=-1;boolean all=true;for(boolean m:matched)all&=m;if(all)won=true;}else memoryHideTimer=.8;}}
    private void updateMemory(double dt){if(secondCard>=0){memoryHideTimer-=dt;if(memoryHideTimer<=0){firstCard=secondCard=-1;}}}
    private void drawMemory(DrawContext ctx){int size=Math.min(width-80,height-100),cell=size/4,ox=(width-cell*4)/2,oy=54+(height-54-cell*4)/2;for(int i=0;i<16;i++){int x=i%4,y=i/4,l=ox+x*cell,t=oy+y*cell;boolean open=matched[i]||i==firstCard||i==secondCard;int c=i==memoryCursor?0xFF63D9E5:(open?0xFF314C5E:0xFF202A35);ctx.fill(l+4,t+4,l+cell-4,t+cell-4,c);if(open)ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(symbol(cards[i])).formatted(Formatting.BOLD),l+cell/2,t+cell/2-4,0xFFFFFFFF);}}
    private String symbol(int v){return new String[]{"A","B","C","D","E","F","G","H"}[v];}

    // ---------- Sokoban ----------
    private void resetSokoban(){String[] map={"############","#     #    #","# $$  # .. #","#  #       #","#  #  ##   #","#  @       #","#     #    #","############"};int h=map.length,w=map[0].length();sokoWalls=new boolean[h][w];sokoGoals=new boolean[h][w];sokoBoxes=new boolean[h][w];for(int y=0;y<h;y++)for(int x=0;x<w;x++){char c=map[y].charAt(x);sokoWalls[y][x]=c=='#';sokoGoals[y][x]=c=='.';sokoBoxes[y][x]=c=='$';if(c=='@'){sokoPX=x;sokoPY=y;}}}
    private void moveSokoban(int dx,int dy){int nx=sokoPX+dx,ny=sokoPY+dy;if(sokoWalls[ny][nx])return;if(sokoBoxes[ny][nx]){int bx=nx+dx,by=ny+dy;if(sokoWalls[by][bx]||sokoBoxes[by][bx])return;sokoBoxes[ny][nx]=false;sokoBoxes[by][bx]=true;}sokoPX=nx;sokoPY=ny;score++;boolean all=true;for(int y=0;y<sokoGoals.length;y++)for(int x=0;x<sokoGoals[0].length;x++)if(sokoGoals[y][x]&&!sokoBoxes[y][x])all=false;if(all)won=true;}
    private void drawSokoban(DrawContext ctx){int h=sokoWalls.length,w=sokoWalls[0].length,cell=Math.max(18,Math.min((width-50)/w,(height-90)/h)),ox=(width-cell*w)/2,oy=55+(height-55-cell*h)/2;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int l=ox+x*cell,t=oy+y*cell;if(sokoWalls[y][x])ctx.fill(l,t,l+cell,t+cell,0xFF49505A);else ctx.fill(l,t,l+cell,t+cell,0xFF151D25);if(sokoGoals[y][x])ctx.fill(l+cell/3,t+cell/3,l+cell*2/3,t+cell*2/3,0xFFFFD659);if(sokoBoxes[y][x])ctx.fill(l+3,t+3,l+cell-3,t+cell-3,0xFFB47742);}int l=ox+sokoPX*cell,t=oy+sokoPY*cell;ctx.fill(l+5,t+5,l+cell-5,t+cell-5,0xFF5DE3E7);}

    // ---------- Frogger ----------
    private void resetFrogger(){frogX=5;frogY=9;for(int i=0;i<carX.length;i++){carLane[i]=1+i%6;carX[i]=random.nextDouble();carSpeed[i]=(i%2==0?1:-1)*(.16+random.nextDouble()*.22);}lives=3;}
    private void updateFrogger(double dt){for(int i=0;i<carX.length;i++){carX[i]=wrap(carX[i]+carSpeed[i]*dt);if(frogY==carLane[i]&&Math.abs(frogX/10.0-carX[i])<.075){lives--;frogX=5;frogY=9;if(lives<=0)gameOver=true;}}}
    private void moveFrog(int dx,int dy){frogX=(int)clamp(frogX+dx,0,10);frogY=(int)clamp(frogY+dy,0,9);if(frogY==0){score+=100;frogX=5;frogY=9;if(score>=500)won=true;}}
    private void drawFrogger(DrawContext ctx){int l=30,t=55,r=width-30,b=height-18,row=(b-t)/10;for(int y=0;y<10;y++){int c=y==0?0xFF388C4B:y==9?0xFF388C4B:(y==3||y==6?0xFF2E6147:0xFF2B3038);ctx.fill(l,t+y*row,r,t+(y+1)*row,c);}for(int i=0;i<carX.length;i++){int x=l+(int)(carX[i]*(r-l)),y=t+carLane[i]*row+row/2;ctx.fill(x-20,y-7,x+20,y+7,i%2==0?0xFFE95B5B:0xFF5BA8E9);}int x=l+(int)(frogX/10.0*(r-l)),y=t+frogY*row+row/2;ctx.fill(x-8,y-8,x+8,y+8,0xFF70F071);ctx.drawTextWithShadow(textRenderer,Text.literal("Lives: "+lives+" • Cross 5 times"),l+5,t+5,0xFFFFFF);}

    // ---------- Platformer ----------
    private void resetPlatformer(){for(boolean[] a:platform)Arrays.fill(a,false);for(boolean[] a:platformCoin)Arrays.fill(a,false);for(int x=0;x<80;x++)platform[x][0]=true;for(int x=5;x<13;x++)platform[x][3]=true;for(int x=18;x<26;x++)platform[x][5]=true;for(int x=31;x<39;x++)platform[x][2]=true;for(int x=43;x<51;x++)platform[x][6]=true;for(int x=57;x<65;x++)platform[x][4]=true;for(int x=70;x<78;x++)platform[x][7]=true;for(int x:new int[]{8,22,35,47,61,74})platformCoin[x][(x==8?4:x==22?6:x==35?3:x==47?7:x==61?5:8)]=true;platX=2;platY=1.01;platVX=platVY=0;lives=3;}
    private boolean platSolid(int x,int y){return x<0||x>=80||y<0||(y<15&&platform[x][y]);}
    private void updatePlatformer(double dt){long win=window();double move=0;if(win!=0){if(down(win,GLFW.GLFW_KEY_A)||down(win,GLFW.GLFW_KEY_LEFT))move-=1;if(down(win,GLFW.GLFW_KEY_D)||down(win,GLFW.GLFW_KEY_RIGHT))move+=1;}platVX=move*5.2;double nx=platX+platVX*dt;if(!platSolid((int)Math.floor(nx),(int)Math.floor(platY)))platX=nx;platVY-=13*dt;double ny=platY+platVY*dt;if(platVY<=0&&platSolid((int)Math.floor(platX),(int)Math.floor(ny))){platY=Math.floor(ny)+1.01;platVY=0;}else platY=ny;if(platY<-2){lives--;platX=2;platY=1;platVY=0;if(lives<=0)gameOver=true;}int cx=(int)Math.floor(platX),cy=(int)Math.round(platY);if(cx>=0&&cx<80&&cy>=0&&cy<15&&platformCoin[cx][cy]){platformCoin[cx][cy]=false;score+=50;}if(platX>77)won=true;}
    private void jumpPlatform(){if(platSolid((int)Math.floor(platX),(int)Math.floor(platY-.08)))platVY=5.6;}
    private void drawPlatformer(DrawContext ctx){int tile=26,cam=(int)clamp(platX-10,0,60),base=height-24;ctx.fill(0,48,width,base,0xFF76B9E9);for(int x=cam;x<Math.min(80,cam+width/tile+2);x++)for(int y=0;y<15;y++){int sx=(x-cam)*tile,sy=base-(y+1)*tile;if(platform[x][y])ctx.fill(sx,sy,sx+tile,sy+tile,0xFF6A8451);if(platformCoin[x][y])ctx.fill(sx+9,sy+9,sx+17,sy+17,0xFFFFD64D);}int px=(int)((platX-cam)*tile),py=base-(int)((platY+1)*tile);ctx.fill(px-8,py,px+8,py+24,0xFF55DDE6);ctx.drawTextWithShadow(textRenderer,Text.literal("Lives: "+lives+" • Reach the far right"),10,52,0xFFFFFF);}

    // ---------- Racing ----------
    private void resetRacing(){raceX=.5;raceSpeed=.58;raceDistance=0;lives=3;for(int i=0;i<raceObsX.length;i++){raceObsX[i]=.25+random.nextDouble()*.5;raceObsY[i]=-random.nextDouble()*1.5;}}
    private void updateRacing(double dt){long win=window();if(win!=0){if(down(win,GLFW.GLFW_KEY_A)||down(win,GLFW.GLFW_KEY_LEFT))raceX-=dt*.55;if(down(win,GLFW.GLFW_KEY_D)||down(win,GLFW.GLFW_KEY_RIGHT))raceX+=dt*.55;if(down(win,GLFW.GLFW_KEY_W)||down(win,GLFW.GLFW_KEY_UP))raceSpeed+=dt*.25;if(down(win,GLFW.GLFW_KEY_S)||down(win,GLFW.GLFW_KEY_DOWN))raceSpeed-=dt*.35;}raceX=clamp(raceX,.21,.79);raceSpeed=clamp(raceSpeed,.32,1.05);raceDistance+=raceSpeed*dt*100;score=(int)raceDistance;for(int i=0;i<raceObsX.length;i++){raceObsY[i]+=raceSpeed*dt*.55;if(raceObsY[i]>1.1){raceObsY[i]=-.2-random.nextDouble();raceObsX[i]=.24+random.nextDouble()*.52;}if(raceObsY[i]>.75&&raceObsY[i]<.95&&Math.abs(raceObsX[i]-raceX)<.065){lives--;raceObsY[i]=-.5;if(lives<=0)gameOver=true;}}if(score>=3000)won=true;}
    private void drawRacing(DrawContext ctx){int l=(int)(width*.18),r=(int)(width*.82),t=50,b=height-18;ctx.fill(0,t,width,b,0xFF294B2A);ctx.fill(l,t,r,b,0xFF292D32);for(int y=t+(int)(raceDistance)%48;y<b;y+=48)ctx.fill(width/2-3,y,width/2+3,Math.min(b,y+24),0xFFE8E8E8);for(int i=0;i<raceObsX.length;i++){int x=(int)(raceObsX[i]*width),y=t+(int)(raceObsY[i]*(b-t));ctx.fill(x-12,y-20,x+12,y+20,0xFFE85252);}int x=(int)(raceX*width);ctx.fill(x-13,b-58,x+13,b-20,0xFF55DDE6);ctx.drawTextWithShadow(textRenderer,Text.literal("Lives: "+lives+" • Finish at 3000m • Speed "+(int)(raceSpeed*100)),l+5,t+5,0xFFFFFF);}

    // ---------- Tower Defense ----------
    private void resetTowerDefense(){for(boolean[] r:towers)Arrays.fill(r,false);Arrays.fill(enemyActive,false);towerCursorX=towerCursorY=0;gold=100;baseHp=20;spawnTimer=0;lives=20;}
    private void buildTower(){if(gold>=25&&!towers[towerCursorY][towerCursorX]){gold-=25;towers[towerCursorY][towerCursorX]=true;}}
    private void updateTowerDefense(double dt){spawnTimer-=dt;if(spawnTimer<=0){for(int i=0;i<enemyActive.length;i++)if(!enemyActive[i]){enemyActive[i]=true;enemyLane[i]=random.nextInt(3);enemyX[i]=1.04;enemyHp[i]=25+score*.25;break;}spawnTimer=Math.max(.6,1.8-score*.003);}for(int i=0;i<enemyActive.length;i++)if(enemyActive[i]){enemyX[i]-=dt*(.045+score*.00002);if(enemyX[i]<.05){enemyActive[i]=false;baseHp-=2;if(baseHp<=0)gameOver=true;}}for(int lane=0;lane<3;lane++)for(int col=0;col<5;col++)if(towers[lane][col]){double tx=.16+col*.14;int target=-1;double best=.36;for(int i=0;i<enemyActive.length;i++)if(enemyActive[i]&&enemyLane[i]==lane&&enemyX[i]>tx){double d=enemyX[i]-tx;if(d<best){best=d;target=i;}}if(target>=0){enemyHp[target]-=dt*16;if(enemyHp[target]<=0){enemyActive[target]=false;gold+=10;score+=10;}}}if(score>=500)won=true;}
    private void drawTowerDefense(DrawContext ctx){int l=25,t=58,r=width-25,b=height-24,row=(b-t)/3;ctx.fill(l,t,r,b,0xFF24402B);for(int y=1;y<3;y++)ctx.fill(l,t+y*row,r,t+y*row+2,0xFF436A48);for(int lane=0;lane<3;lane++)for(int col=0;col<5;col++){int x=l+(int)((.16+col*.14)*(r-l)),y=t+lane*row+row/2;if(towers[lane][col])ctx.fill(x-10,y-14,x+10,y+14,0xFF5DD9E5);if(lane==towerCursorY&&col==towerCursorX)ctx.fill(x-14,y-18,x+14,y-15,0xFFFFFF66);}for(int i=0;i<enemyActive.length;i++)if(enemyActive[i]){int x=l+(int)(enemyX[i]*(r-l)),y=t+enemyLane[i]*row+row/2;ctx.fill(x-9,y-9,x+9,y+9,0xFFE85B5B);}ctx.drawTextWithShadow(textRenderer,Text.literal("Gold: "+gold+" • Base HP: "+baseHp+" • Survive to 500 score"),l+5,t-13,0xFFFFFF);}

    // ---------- Dungeon ----------
    private void resetDungeon(){dungeonX=1;dungeonY=1;dungeonHp=5;crystals=0;for(boolean[] r:crystalTaken)Arrays.fill(r,false);int[][] starts={{4,3},{8,3},{13,5},{5,7},{11,9},{8,10}};for(int i=0;i<6;i++){dungeonEX[i]=starts[i][0];dungeonEY[i]=starts[i][1];dungeonEnemyAlive[i]=true;}}
    private boolean dungeonWall(int x,int y){return y<0||y>=DUNGEON_MAP.length||x<0||x>=DUNGEON_MAP[0].length()||DUNGEON_MAP[y].charAt(x)=='#';}
    private void moveDungeon(int dx,int dy){int nx=dungeonX+dx,ny=dungeonY+dy;if(dungeonWall(nx,ny))return;for(int i=0;i<6;i++)if(dungeonEnemyAlive[i]&&dungeonEX[i]==nx&&dungeonEY[i]==ny)return;dungeonX=nx;dungeonY=ny;if(DUNGEON_MAP[ny].charAt(nx)=='*'&&!crystalTaken[ny][nx]){crystalTaken[ny][nx]=true;crystals++;score+=100;}if(DUNGEON_MAP[ny].charAt(nx)=='E'&&crystals>=3)won=true;dungeonEnemiesTurn();}
    private void dungeonAttack(){for(int i=0;i<6;i++)if(dungeonEnemyAlive[i]&&Math.abs(dungeonEX[i]-dungeonX)+Math.abs(dungeonEY[i]-dungeonY)==1){dungeonEnemyAlive[i]=false;score+=25;dungeonEnemiesTurn();return;}}
    private void dungeonEnemiesTurn(){for(int i=0;i<6;i++)if(dungeonEnemyAlive[i]){int dx=Integer.compare(dungeonX,dungeonEX[i]),dy=Integer.compare(dungeonY,dungeonEY[i]);int nx=dungeonEX[i]+dx,ny=dungeonEY[i];if(dx==0||dungeonWall(nx,ny)){nx=dungeonEX[i];ny=dungeonEY[i]+dy;}if(nx==dungeonX&&ny==dungeonY){dungeonHp--;if(dungeonHp<=0)gameOver=true;}else if(!dungeonWall(nx,ny)&&!dungeonEnemyAt(nx,ny,i)){dungeonEX[i]=nx;dungeonEY[i]=ny;}}}
    private boolean dungeonEnemyAt(int x,int y,int skip){for(int i=0;i<6;i++)if(i!=skip&&dungeonEnemyAlive[i]&&dungeonEX[i]==x&&dungeonEY[i]==y)return true;return false;}
    private void drawDungeon(DrawContext ctx){int h=DUNGEON_MAP.length,w=DUNGEON_MAP[0].length(),cell=Math.max(14,Math.min((width-50)/w,(height-90)/h)),ox=(width-cell*w)/2,oy=54+(height-54-cell*h)/2;for(int y=0;y<h;y++)for(int x=0;x<w;x++){char c=DUNGEON_MAP[y].charAt(x);int l=ox+x*cell,t=oy+y*cell;ctx.fill(l,t,l+cell,t+cell,c=='#'?0xFF3A3D45:0xFF131A22);if(c=='E')ctx.fill(l+4,t+4,l+cell-4,t+cell-4,0xFF5B8FE8);if(c=='*'&&!crystalTaken[y][x])ctx.fill(l+cell/3,t+cell/3,l+cell*2/3,t+cell*2/3,0xFFFFD85C);}for(int i=0;i<6;i++)if(dungeonEnemyAlive[i]){int l=ox+dungeonEX[i]*cell,t=oy+dungeonEY[i]*cell;ctx.fill(l+4,t+4,l+cell-4,t+cell-4,0xFFE85B5B);}int l=ox+dungeonX*cell,t=oy+dungeonY*cell;ctx.fill(l+3,t+3,l+cell-3,t+cell-3,0xFF62E4E0);ctx.drawTextWithShadow(textRenderer,Text.literal("HP: "+dungeonHp+" • Crystals: "+crystals+"/3 • Get all 3, then reach blue exit"),ox,oy-13,0xFFFFFF);}

    @Override
    public boolean keyPressed(KeyInput input) {
        int k=input.key();
        if(k==GLFW.GLFW_KEY_ESCAPE||k==GLFW.GLFW_KEY_F9){if(client!=null)client.setScreen(parent);return true;}
        if(k==GLFW.GLFW_KEY_R){resetGame();return true;}
        if(gameOver||won)return true;
        switch(game){
            case SNAKE -> {if((k==GLFW.GLFW_KEY_UP||k==GLFW.GLFW_KEY_W)&&snakeDy!=1){nextDx=0;nextDy=-1;}if((k==GLFW.GLFW_KEY_DOWN||k==GLFW.GLFW_KEY_S)&&snakeDy!=-1){nextDx=0;nextDy=1;}if((k==GLFW.GLFW_KEY_LEFT||k==GLFW.GLFW_KEY_A)&&snakeDx!=1){nextDx=-1;nextDy=0;}if((k==GLFW.GLFW_KEY_RIGHT||k==GLFW.GLFW_KEY_D)&&snakeDx!=-1){nextDx=1;nextDy=0;}}
            case ASTEROIDS -> {if(k==GLFW.GLFW_KEY_SPACE)fireAsteroidShot();}
            case INVADERS -> {if(k==GLFW.GLFW_KEY_SPACE&&pShotY<0){pShotX=invPlayerX;pShotY=.84;}}
            case FLAPPY -> {if(k==GLFW.GLFW_KEY_SPACE)flap();}
            case GAME_2048 -> {if(k==GLFW.GLFW_KEY_LEFT)move2048(-1,0);if(k==GLFW.GLFW_KEY_RIGHT)move2048(1,0);if(k==GLFW.GLFW_KEY_UP)move2048(0,-1);if(k==GLFW.GLFW_KEY_DOWN)move2048(0,1);}
            case MINESWEEPER -> {if(k==GLFW.GLFW_KEY_LEFT)mineCursorX=Math.max(0,mineCursorX-1);if(k==GLFW.GLFW_KEY_RIGHT)mineCursorX=Math.min(MINE_W-1,mineCursorX+1);if(k==GLFW.GLFW_KEY_UP)mineCursorY=Math.max(0,mineCursorY-1);if(k==GLFW.GLFW_KEY_DOWN)mineCursorY=Math.min(MINE_H-1,mineCursorY+1);if(k==GLFW.GLFW_KEY_ENTER)revealMine(mineCursorX,mineCursorY);if(k==GLFW.GLFW_KEY_F&&!revealed[mineCursorY][mineCursorX])flagged[mineCursorY][mineCursorX]=!flagged[mineCursorY][mineCursorX];}
            case MEMORY -> {if(k==GLFW.GLFW_KEY_LEFT)memoryCursor=(memoryCursor+15)%16;if(k==GLFW.GLFW_KEY_RIGHT)memoryCursor=(memoryCursor+1)%16;if(k==GLFW.GLFW_KEY_UP)memoryCursor=(memoryCursor+12)%16;if(k==GLFW.GLFW_KEY_DOWN)memoryCursor=(memoryCursor+4)%16;if(k==GLFW.GLFW_KEY_ENTER)flipMemory();}
            case SOKOBAN -> {if(k==GLFW.GLFW_KEY_LEFT)moveSokoban(-1,0);if(k==GLFW.GLFW_KEY_RIGHT)moveSokoban(1,0);if(k==GLFW.GLFW_KEY_UP)moveSokoban(0,-1);if(k==GLFW.GLFW_KEY_DOWN)moveSokoban(0,1);}
            case FROGGER -> {if(k==GLFW.GLFW_KEY_LEFT)moveFrog(-1,0);if(k==GLFW.GLFW_KEY_RIGHT)moveFrog(1,0);if(k==GLFW.GLFW_KEY_UP)moveFrog(0,-1);if(k==GLFW.GLFW_KEY_DOWN)moveFrog(0,1);}
            case PLATFORMER -> {if(k==GLFW.GLFW_KEY_SPACE)jumpPlatform();}
            case TOWER_DEFENSE -> {if(k==GLFW.GLFW_KEY_LEFT)towerCursorX=Math.max(0,towerCursorX-1);if(k==GLFW.GLFW_KEY_RIGHT)towerCursorX=Math.min(4,towerCursorX+1);if(k==GLFW.GLFW_KEY_UP)towerCursorY=Math.max(0,towerCursorY-1);if(k==GLFW.GLFW_KEY_DOWN)towerCursorY=Math.min(2,towerCursorY+1);if(k==GLFW.GLFW_KEY_ENTER)buildTower();}
            case DUNGEON -> {if(k==GLFW.GLFW_KEY_LEFT)moveDungeon(-1,0);if(k==GLFW.GLFW_KEY_RIGHT)moveDungeon(1,0);if(k==GLFW.GLFW_KEY_UP)moveDungeon(0,-1);if(k==GLFW.GLFW_KEY_DOWN)moveDungeon(0,1);if(k==GLFW.GLFW_KEY_E)dungeonAttack();}
            default -> { }
        }
        return super.keyPressed(input);
    }

    private long window(){return client==null?0:client.getWindow().getHandle();}
    private static boolean down(long w,int k){return GLFW.glfwGetKey(w,k)==GLFW.GLFW_PRESS;}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static double wrap(double v){while(v<0)v+=1;while(v>=1)v-=1;return v;}
    private static double dist(double ax,double ay,double bx,double by){double x=ax-bx,y=ay-by;return Math.sqrt(x*x+y*y);}

    @Override public boolean shouldPause(){return false;}
}
