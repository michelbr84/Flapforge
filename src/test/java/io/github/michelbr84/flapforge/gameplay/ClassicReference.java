package io.github.michelbr84.flapforge.gameplay;

import java.util.Random;

/**
 * Literal transliteration of the upstream {@code Bird.freeFall / birdFlap / movement} loop
 * (kingyuluk/FlappyBird, commit {@code b811782}) plus the spawn rules of
 * {@code GameElementLayer.pipeBornLogic}: integers, 30 Hz frames, up-positive velocity.
 *
 * <p>Every quirk is kept on purpose so {@code ClassicFeelTest} and {@code GroundRuleTest} can
 * measure the converted 60 Hz model against the original:
 *
 * <ul>
 *   <li>{@code MAX_VEL_Y = 15} is dead code: {@code velocity < 15} is always true while falling,
 *       so the descent is never clamped;</li>
 *   <li>{@code BOTTOM_BOUNDARY} was an instance-field initialiser reading the static
 *       {@code BIRD_HEIGHT} before the constructor assigned it, so it evaluates to
 *       {@code 640 − 42 − 0 = 598}: the centre is clamped at 598 (sprite half buried) while the
 *       collision rectangle keeps moving unclamped, and death only fires when the unclamped
 *       {@code rect.y > 598} (centre above 610);</li>
 *   <li>a flap is accepted while {@code rect.y > TOP_BAR_HEIGHT (20)} — also while buried.</li>
 * </ul>
 */
final class ClassicReference {

    /** Upstream {@code Constant.FRAME_WIDTH}. */
    static final int FRAME_WIDTH = 420;
    /** Upstream {@code Constant.FRAME_HEIGHT}. */
    static final int FRAME_HEIGHT = 640;
    /** Upstream {@code Bird.ACC_FLAP}. */
    static final int ACC_FLAP = 14;
    /** Upstream {@code Bird.ACC_Y} (declared {@code double}, always used as an int step). */
    static final int ACC_Y = 2;
    /** Upstream {@code Bird.MAX_VEL_Y} (dead code, kept for the literal condition). */
    static final int MAX_VEL_Y = 15;
    /** Upstream {@code Constant.TOP_BAR_HEIGHT}: the flap gate on {@code rect.y}. */
    static final int TOP_BAR_HEIGHT = 20;
    /** Upstream {@code GameBackground.GROUND_HEIGHT} (background image height / 2). */
    static final int GROUND_HEIGHT = 42;
    /** Upstream bird sprite size ({@code 0.png}). */
    static final int BIRD_WIDTH = 39;
    /** Upstream bird sprite height. */
    static final int BIRD_HEIGHT = 33;
    /** Upstream {@code Bird.RECT_DESCALE}. */
    static final int RECT_DESCALE = 2;
    /** Value of the static {@code BIRD_HEIGHT} when the field initialiser ran. */
    static final int BIRD_HEIGHT_AT_FIELD_INIT = 0;
    /** The field-initialiser quirk: {@code 640 − 42 − 0 / 2 = 598}. */
    static final int BOTTOM_BOUNDARY = FRAME_HEIGHT - GROUND_HEIGHT - BIRD_HEIGHT_AT_FIELD_INIT / 2;
    /** Collision rectangle offset: {@code −BIRD_WIDTH/2 + RECT_DESCALE = −17}. */
    static final int RECT_DX = -(BIRD_WIDTH / 2) + RECT_DESCALE;
    /** Collision rectangle offset: {@code −BIRD_HEIGHT/2 + 2·RECT_DESCALE = −12}. */
    static final int RECT_DY = -(BIRD_HEIGHT / 2) + RECT_DESCALE * 2;
    /** Collision rectangle width: {@code BIRD_WIDTH − 3·RECT_DESCALE = 33}. */
    static final int RECT_W = BIRD_WIDTH - RECT_DESCALE * 3;
    /** Collision rectangle height, derived from the WIDTH upstream: {@code 39 − 8 = 31}. */
    static final int RECT_H = BIRD_WIDTH - RECT_DESCALE * 4;
    /** Upstream start position ({@code FRAME_HEIGHT >> 1}). */
    static final int START_Y = FRAME_HEIGHT >> 1;
    /** Upstream bird x ({@code FRAME_WIDTH >> 2}). */
    static final int BIRD_X = FRAME_WIDTH >> 2;

    /** Upstream {@code Constant.GAME_SPEED}: pixels per frame. */
    static final int GAME_SPEED = 4;
    /** Upstream pipe body image width. */
    static final int PIPE_WIDTH = 40;
    /** Upstream pipe cap image width. */
    static final int PIPE_HEAD_WIDTH = 44;
    /** Upstream {@code Constant.TOP_PIPE_LENGTHENING}. */
    static final int TOP_PIPE_LENGTHENING = 100;
    /** Upstream {@code GameElementLayer.VERTICAL_INTERVAL = 640 / 5}. */
    static final int VERTICAL_INTERVAL = FRAME_HEIGHT / 5;
    /** Upstream {@code GameElementLayer.HORIZONTAL_INTERVAL = 640 >> 2}. */
    static final int HORIZONTAL_INTERVAL = FRAME_HEIGHT >> 2;
    /** Upstream {@code GameElementLayer.MIN_HEIGHT = 640 >> 3}. */
    static final int MIN_HEIGHT = FRAME_HEIGHT >> 3;
    /** Upstream {@code GameElementLayer.MAX_HEIGHT = (640 >> 3) * 5}. */
    static final int MAX_HEIGHT = (FRAME_HEIGHT >> 3) * 5;
    /** Hover pipe y lower bound ({@code 640 / 12}). */
    static final int HOVER_Y_MIN = FRAME_HEIGHT / 12;
    /** Hover pipe y exclusive upper bound ({@code 640 / 6}). */
    static final int HOVER_Y_MAX = FRAME_HEIGHT / 6;
    /** Hover pipe height lower bound ({@code 640 / 6}). */
    static final int HOVER_H_MIN = FRAME_HEIGHT / 6;
    /** Hover pipe height exclusive upper bound ({@code 640 / 4}). */
    static final int HOVER_H_MAX = FRAME_HEIGHT / 4;
    /** Upstream {@code MovingPipe.MAX_DELTA}; the flip fires when {@code dealtY > 50}. */
    static final int MAX_DELTA = 50;
    /** First pipe pair x. */
    static final int FIRST_X = FRAME_WIDTH;

    /** Upstream bird states. */
    enum State {
        NORMAL, UP, FALL, DEAD_FALL, DEAD
    }

    /** A rectangle in upstream's integer coordinates. */
    record Rect(int x, int y, int w, int h) {
    }

    private int y;
    private int velocity;
    private int rectY;
    private State state = State.NORMAL;

    /** Starts at {@link #START_Y}. */
    ClassicReference() {
        this(START_Y);
    }

    /**
     * Starts at an arbitrary centre y (rectangle at {@code y + RECT_DY}).
     *
     * @param startY the centre y
     */
    ClassicReference(int startY) {
        y = startY;
        rectY = startY + RECT_DY;
    }

    /**
     * {@code Bird.birdFlap()} without the key-flag debounce: {@code state = UP}, then
     * {@code velocity = ACC_FLAP} only if {@code rect.y > TOP_BAR_HEIGHT}.
     *
     * @return {@code true} when the flap set the velocity
     */
    boolean birdFlap() {
        if (isDead()) {
            return false;
        }
        state = State.UP;
        if (rectY > TOP_BAR_HEIGHT) {
            velocity = ACC_FLAP;
            return true;
        }
        return false;
    }

    /** {@code Bird.birdFall()}: gravity applies from the next frame on. */
    void birdFall() {
        if (isDead()) {
            return;
        }
        state = State.FALL;
    }

    /** {@code Bird.deadBirdFall()}: hit a pipe; velocity zeroed, keeps falling. */
    void deadBirdFall() {
        state = State.DEAD_FALL;
        velocity = 0;
    }

    /** One 30 Hz frame of {@code Bird.movement()} (wing state omitted). */
    void frame() {
        if (state == State.FALL || state == State.DEAD_FALL) {
            freeFall();
            if (rectY > BOTTOM_BOUNDARY) {
                die();
            }
        }
    }

    private void freeFall() {
        if (velocity < MAX_VEL_Y) {
            velocity -= ACC_Y;
        }
        y = Math.min(y - velocity, BOTTOM_BOUNDARY);
        rectY = rectY - velocity;
    }

    private void die() {
        state = State.DEAD;
    }

    /**
     * {@code Bird.isDead()}.
     *
     * @return {@code true} in {@code DEAD_FALL} or {@code DEAD}
     */
    boolean isDead() {
        return state == State.DEAD_FALL || state == State.DEAD;
    }

    /**
     * Tells whether the bird has died on the ground ({@code rect.y > 598}).
     *
     * @return {@code true} in {@code DEAD}
     */
    boolean isOnGround() {
        return state == State.DEAD;
    }

    /**
     * Sprite centre y, clamped at {@link #BOTTOM_BOUNDARY}.
     *
     * @return the y
     */
    int y() {
        return y;
    }

    /**
     * Up-positive velocity in px/frame.
     *
     * @return the velocity
     */
    int velocity() {
        return velocity;
    }

    /**
     * Collision rectangle top (unclamped).
     *
     * @return {@code rect.y}
     */
    int rectY() {
        return rectY;
    }

    /**
     * Centre y that ignores the ground clamp ({@code rect.y − RECT_DY}).
     *
     * @return the unclamped centre
     */
    int unclampedY() {
        return rectY - RECT_DY;
    }

    /**
     * The state.
     *
     * @return the state
     */
    State state() {
        return state;
    }

    /**
     * Upstream collision rectangle.
     *
     * @return {@code (x − 17, rect.y, 33, 31)}
     */
    Rect rect() {
        return new Rect(BIRD_X + RECT_DX, rectY, RECT_W, RECT_H);
    }

    // ---- spawn rules (GameElementLayer.pipeBornLogic) ----

    /**
     * {@code GameUtil.isInProbability(n, d)}: {@code getRandomNumber(1, d + 1) <= n}.
     *
     * @param random the generator
     * @param numerator the numerator
     * @param denominator the denominator
     * @return {@code true} with probability {@code n / d}
     */
    static boolean isInProbability(Random random, int numerator, int denominator) {
        if (numerator >= denominator) {
            return true;
        }
        return 1 + random.nextInt(denominator) <= numerator;
    }

    /**
     * Probability of a moving pipe pair: {@code isInProbability(score + 1, 20)}.
     *
     * @param score the current score
     * @return the probability, capped at 1
     */
    static double movingProbability(int score) {
        return Math.min(1.0, (score + 1) / 20.0);
    }

    /** Share of moving pairs that hover: {@code isInProbability(1, 4)}. */
    static final double MOVING_HOVER_SHARE = 0.25;
    /** Share of static pairs that are normal: {@code isInProbability(1, 2)}. */
    static final double STATIC_NORMAL_SHARE = 0.5;

    /**
     * {@code Pipe.isInFrame()}: the last pair is fully inside the window.
     *
     * @param lastX the x of the last pipe
     * @return {@code true} when the next pair may spawn
     */
    static boolean shouldSpawnNext(int lastX) {
        return lastX + PIPE_WIDTH < FRAME_WIDTH;
    }

    /**
     * x of the next pair.
     *
     * @param lastX the x of the last pipe
     * @return {@code lastX + HORIZONTAL_INTERVAL}
     */
    static int nextX(int lastX) {
        return lastX + HORIZONTAL_INTERVAL;
    }

    /**
     * Normal top pipe rectangle ({@code addNormalPipe}).
     *
     * @param x the pair x
     * @param topHeight the height of the gap top, in {@code [MIN_HEIGHT, MAX_HEIGHT]}
     * @return the rectangle
     */
    static Rect normalTop(int x, int topHeight) {
        return new Rect(x, -TOP_PIPE_LENGTHENING, PIPE_WIDTH, topHeight + TOP_PIPE_LENGTHENING);
    }

    /**
     * Normal bottom pipe rectangle ({@code addNormalPipe}).
     *
     * @param x the pair x
     * @param topHeight the height of the gap top
     * @return the rectangle
     */
    static Rect normalBottom(int x, int topHeight) {
        return new Rect(x, topHeight + VERTICAL_INTERVAL, PIPE_WIDTH,
                FRAME_HEIGHT - topHeight - VERTICAL_INTERVAL);
    }

    /**
     * Upper hover pipe rectangle ({@code addHoverPipe}).
     *
     * @param x the pair x
     * @param y the upper pipe top
     * @param topHoverHeight the upper pipe height
     * @return the rectangle
     */
    static Rect hoverTop(int x, int y, int topHoverHeight) {
        return new Rect(x, y, PIPE_WIDTH, topHoverHeight);
    }

    /**
     * Lower hover pipe rectangle ({@code addHoverPipe}).
     *
     * @param x the pair x
     * @param y the upper pipe top
     * @param topHoverHeight the upper pipe height
     * @return the rectangle
     */
    static Rect hoverBottom(int x, int y, int topHoverHeight) {
        int bottomHoverHeight = FRAME_HEIGHT - 2 * y - topHoverHeight - VERTICAL_INTERVAL;
        return new Rect(x, y + topHoverHeight + VERTICAL_INTERVAL, PIPE_WIDTH, bottomHoverHeight);
    }
}
