package com.codingame.game.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Byte Club physics. Two fighters of 19 points each, seen from the side. Units: metres, kilograms, seconds, radians.
 * Rigid bones and joint ranges are enforced on positions; joint motors are PD controllers whose torque is capped by the
 * strength asked and falls with speed; the floor has friction; the fighters collide and hit each other.
 * One turn is 40 steps of 2.5 ms. Only + - * / and square roots are used during a step, so the same code gives the same
 * numbers in every language. Nothing depends on which fighter is called 0: a world and its mirror image stay mirror images.
 */
public final class Physics {
    public static final double G = 9.81, RING = 3.0, MU = 0.9;
    public static final int STEPS = 40;
    public static final double DT = 0.0025, DRAG = 0.99975;
    public static final double PI = 3.141592653589793, DEG2RAD = 0.017453292519943295, RAD2DEG = 57.29577951308232;

    // ---------- body
    public static final int HEAD = 0, NECK = 1, CHEST = 2, WAIST = 3, PELVIS = 4,
        LSH = 5, LELB = 6, LHAND = 7, RSH = 8, RELB = 9, RHAND = 10,
        LKNEE = 11, LANK = 12, LHEEL = 13, LTOE = 14, RKNEE = 15, RANK = 16, RHEEL = 17, RTOE = 18, NP = 19;
    public static final String[] PNAME = {"HEAD", "NECK", "CHEST", "WAIST", "PELVIS", "LEAD_SHOULDER", "LEAD_ELBOW", "LEAD_HAND",
        "REAR_SHOULDER", "REAR_ELBOW", "REAR_HAND", "LEAD_KNEE", "LEAD_ANKLE", "LEAD_HEEL", "LEAD_TOES",
        "REAR_KNEE", "REAR_ANKLE", "REAR_HEEL", "REAR_TOES"};
    public static final double[] MASS = {5, 6, 9, 9, 12, 2, 1.5, 1.2, 2, 1.5, 1.2, 4, 2, 0.5, 1, 4, 2, 0.5, 1};
    public static final double[] RAD = {0.11, 0.12, 0.13, 0.12, 0.13, 0.06, 0.05, 0.065, 0.06, 0.05, 0.065, 0.08, 0.04, 0.03, 0.03, 0.08, 0.04, 0.03, 0.03};
    // foot: heel 5 cm behind and 6 cm below the ankle, toes 17 cm in front and 6 cm below
    public static final double HEELX = 0.05, TOEX = 0.17, FOOTY = 0.06, ANKH = 0.09;
    public static final double LAH = 0.07810249675906655, LAT = 0.18027756377319948, LHT = 0.22000000000000003;
    public static final double GIRDLE = 0.13, UARM = 0.30, FARM = 0.28, THIGH = 0.45, SHIN = 0.45;
    public static final int[][] BONE = {{NECK, HEAD}, {CHEST, NECK}, {WAIST, CHEST}, {PELVIS, WAIST},
        {NECK, LSH}, {LSH, LELB}, {LELB, LHAND}, {NECK, RSH}, {RSH, RELB}, {RELB, RHAND},
        {PELVIS, LKNEE}, {LKNEE, LANK}, {LANK, LHEEL}, {LANK, LTOE}, {LHEEL, LTOE},
        {PELVIS, RKNEE}, {RKNEE, RANK}, {RANK, RHEEL}, {RANK, RTOE}, {RHEEL, RTOE}};
    public static final double[] BLEN = {0.20, 0.18, 0.18, 0.14, GIRDLE, UARM, FARM, GIRDLE, UARM, FARM,
        THIGH, SHIN, LAH, LAT, LHT, THIGH, SHIN, LAH, LAT, LHT};
    public static final boolean[] FOOTPT = new boolean[NP];
    static { FOOTPT[LANK] = FOOTPT[LHEEL] = FOOTPT[LTOE] = FOOTPT[RANK] = FOOTPT[RHEEL] = FOOTPT[RTOE] = true; }

    // ---------- joints: angle of the child vector C - J from the reference vector B - A, in the fighter's own frame
    // (handedness JK, offset JOFF). This is the order of the JOINTS command.
    public static final int NK = 0, UB = 1, LB = 2, LG = 3, LS = 4, LE = 5, RG = 6, RS = 7, RE = 8, LH = 9, LK = 10, LA = 11, RH = 12, RK = 13, RA = 14, NJ = 15;
    public static final String[] JNAME = {"NK", "UB", "LB", "LG", "LS", "LE", "RG", "RS", "RE", "LH", "LK", "LA", "RH", "RK", "RA"};
    public static final String[] JLONG = {"NECK", "UPPER_BACK", "LOWER_BACK", "LEAD_GIRDLE", "LEAD_SHOULDER", "LEAD_ELBOW",
        "REAR_GIRDLE", "REAR_SHOULDER", "REAR_ELBOW", "LEAD_HIP", "LEAD_KNEE", "LEAD_ANKLE", "REAR_HIP", "REAR_KNEE", "REAR_ANKLE"};
    public static final int[] JA = {CHEST, WAIST, PELVIS, NECK, NECK, LSH, NECK, NECK, RSH, NECK, PELVIS, LANK, NECK, PELVIS, RANK};
    public static final int[] JB = {NECK, CHEST, WAIST, PELVIS, PELVIS, LELB, PELVIS, PELVIS, RELB, PELVIS, LKNEE, LKNEE, PELVIS, RKNEE, RKNEE};
    public static final int[] JJ = {NECK, CHEST, WAIST, NECK, LSH, LELB, NECK, RSH, RELB, PELVIS, LKNEE, LANK, PELVIS, RKNEE, RANK};
    public static final int[] JC = {HEAD, NECK, CHEST, LSH, LELB, LHAND, RSH, RELB, RHAND, LKNEE, LANK, LTOE, RKNEE, RANK, RTOE};
    public static final int[] JK = {-1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1, -1, 1, -1, -1};
    public static final double[] JOFF = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.9100889412489412, 0, 0, 1.9100889412489412};
    public static final double[] JMIN = {-30, -20, -25, -60, -60, 0, -60, -60, 0, -45, 0, -40, -45, 0, -40};
    public static final double[] JMAX = {45, 35, 40, 60, 180, 150, 60, 180, 150, 130, 150, 50, 130, 150, 50};
    public static final int[] JMINC = new int[NJ], JMAXC = new int[NJ];          // the same ranges in hundredths of a degree
    static { for (int j = 0; j < NJ; j++) { JMINC[j] = (int) Math.rint(JMIN[j] * 100); JMAXC[j] = (int) Math.rint(JMAX[j] * 100); } }
    public static final double[] TMAX = {40, 120, 150, 120, 80, 60, 120, 80, 60, 220, 220, 150, 220, 220, 150};
    // a motor's torque falls linearly with its speed: zero at WMAX (rad/s); braking keeps the full torque
    public static final double[] WMAX = {10, 10, 10, 15, 40, 60, 15, 40, 60, 25, 50, 20, 25, 50, 20};
    // gains of the motors: KP = TMAX over 10 degrees (5 for the ankles); KD = damping; KDFLAT = damping of an ankle whose foot is flat
    public static final double[] KP = {229.1831180523293, 687.5493541569879, 859.4366926962349, 687.5493541569879, 458.3662361046586, 343.77467707849394, 687.5493541569879, 458.3662361046586, 343.77467707849394, 1260.5071492878112, 1260.5071492878112, 1718.8733853924698, 1260.5071492878112, 1260.5071492878112, 1718.8733853924698};
    public static final double[] KD = {4.541638539636288, 10.155412503859614, 11.354096349090721, 4.541638539636288, 9.574614729634385, 5.684612532563537, 4.541638539636288, 9.574614729634385, 5.684612532563537, 75.06308651605339, 28.623934862926816, 7.1809610472257885, 75.06308651605339, 28.623934862926816, 7.1809610472257885};
    public static final double[] KDFLAT = {4.541638539636288, 10.155412503859614, 11.354096349090721, 4.541638539636288, 9.574614729634385, 5.684612532563537, 4.541638539636288, 9.574614729634385, 5.684612532563537, 75.06308651605339, 28.623934862926816, 343.5465571863146, 75.06308651605339, 28.623934862926816, 343.5465571863146};
    public static final double[] JLO = {-0.5235987755982988, -0.3490658503988659, -0.4363323129985824, -1.0471975511965976, -1.0471975511965976, 0.0, -1.0471975511965976, -1.0471975511965976, 0.0, -0.7853981633974483, 0.0, -0.6981317007977318, -0.7853981633974483, 0.0, -0.6981317007977318};
    public static final double[] JHI = {0.7853981633974483, 0.6108652381980153, 0.6981317007977318, 1.0471975511965976, 3.141592653589793, 2.6179938779914944, 1.0471975511965976, 3.141592653589793, 2.6179938779914944, 2.2689280275926285, 2.6179938779914944, 0.8726646259971648, 2.2689280275926285, 2.6179938779914944, 0.8726646259971648};
    public static final double[] JCEN = {0.13089969389957473, 0.1308996938995747, 0.1308996938995747, 0.0, 1.0471975511965979, 1.3089969389957472, 0.0, 1.0471975511965979, 1.3089969389957472, 0.7417649320975901, 1.3089969389957472, 0.08726646259971649, 0.7417649320975901, 1.3089969389957472, 0.08726646259971649};

    // ---------- shapes: a, b, kind; a == b is a circle, otherwise a capsule
    // kinds: 0 head, 1 torso, 2 arm, 3 glove, 4 thigh, 5 foot, 6 shin, 7 knee (the knees only strike: never pushed, never hit)
    public static final int[][] SH = {{HEAD, HEAD, 0}, {NECK, CHEST, 1}, {CHEST, WAIST, 1}, {WAIST, PELVIS, 1},
        {LSH, LELB, 2}, {LELB, LHAND, 2}, {LHAND, LHAND, 3}, {RSH, RELB, 2}, {RELB, RHAND, 2}, {RHAND, RHAND, 3},
        {PELVIS, LKNEE, 4}, {LKNEE, LANK, 6}, {LHEEL, LTOE, 5}, {PELVIS, RKNEE, 4}, {RKNEE, RANK, 6}, {RHEEL, RTOE, 5},
        {LKNEE, LKNEE, 7}, {RKNEE, RKNEE, 7}};
    public static final double[] SR = {0.11, 0.13, 0.12, 0.13, 0.05, 0.045, 0.065, 0.05, 0.045, 0.065, 0.08, 0.05, 0.03, 0.08, 0.05, 0.03, 0.075, 0.075};
    public static final int[] SIDE = {0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 1, 1, 1, 2, 2, 2, 1, 2};      // 0 middle, 1 lead side, 2 rear side
    public static final String[] KNAME = {"head", "torso", "arm", "glove", "thigh", "foot", "shin", "knee"};
    public static final int NS = 18, N = 2 * NP;
    public static final int REAR_FOREARM_SH = 8, REAR_GLOVE_SH = 9;
    public static final boolean[] LEADLEG = new boolean[NS];           // the shapes of the lead leg: thigh, shin, foot, knee
    static { LEADLEG[10] = LEADLEG[11] = LEADLEG[12] = LEADLEG[16] = true; }
    public static double zone(int kind) { return kind == 0 ? 2 : kind == 1 ? 1 : (kind == 4 || kind == 5 || kind == 6) ? 0.5 : 0; }
    public static boolean striker(int kind) { return kind == 3 || kind == 5 || kind == 7; }
    /** The six shapes that strike, in the order of the input: lead glove, rear glove, lead foot, rear foot, lead knee, rear knee. */
    public static final int[] STRIKERS = {6, 9, 12, 15, 16, 17};
    // one hit per contact: a glove, foot or knee scores again only after SEP steps away from the opponent
    public static final int SEP = 10;
    public static final double FOOTMIN = 0.30, STRIKEV = 1.0;
    // two shapes that already overlap at the start of a step are pushed apart by BIAS per step at most (1 m/s)
    public static final double BIAS = 0.0025;

    public static final double[] RADN = new double[N], GRAV = new double[N];
    static { for (int i = 0; i < N; i++) { RADN[i] = RAD[i % NP]; GRAV[i] = -G * MASS[i % NP]; } }
    public static final int[] FACING = {1, -1};
    static final int[] KIND = new int[NS];
    static final boolean[] STRIKES = new boolean[NS];
    static final boolean[][] PASS = new boolean[NS][NS];        // pairs of shapes that never meet
    static {
        for (int i = 0; i < NS; i++) { KIND[i] = SH[i][2]; STRIKES[i] = striker(KIND[i]); }
        for (int i = 0; i < NS; i++) for (int j = 0; j < NS; j++) PASS[i][j] = passShapes(i, j) || (KIND[i] == 7 && KIND[j] == 7);
    }

    // ---------- state
    public final double[] px = new double[N], py = new double[N], vx = new double[N], vy = new double[N];
    final double[] fx = new double[N], fy = new double[N], sx = new double[N], sy = new double[N], w = new double[N], dep = new double[N];
    public final boolean[] touch = new boolean[N];
    final boolean[] wasTouch = new boolean[N];
    public final double[][] target = new double[2][NJ], strength = new double[2][NJ];      // degrees; 0..1
    public final int[] hp = {100, 100};
    /** HP taken by the strikes of this turn: the strikes are found at each step, and the HP are taken off at the end of the turn. */
    public final int[] damage = new int[2];
    public final boolean[] down = new boolean[2], out = new boolean[2];
    public final int[] downPt = {-1, -1};                    // the first point other than a foot that touched the floor this turn
    public final int[] fallStep = {-1, -1};                  // the step of this turn at which the fighter went down or out
    public final double[] fatigue = {1, 1};
    public final int[] apart = new int[2 * NS];              // steps since each shape last touched the opponent, SEP at most
    public final boolean[] roundLeg = new boolean[2];        // the lead leg passes beside the opponent's body this turn
    public final int[] blockedBy = new int[2];
    public int contactSteps, stepNo;
    public boolean record = true;
    public final List<double[]> hitEvents = new ArrayList<>();      // {step, attacker, striker shape, target shape, speed, damage, x, y}
    public final List<double[]> blockEvents = new ArrayList<>();    // {step, attacker, x, y}: a strike stopped by the arms (shown by the viewer only)
    boolean near, contactNow;
    final boolean[] touching = new boolean[2 * NS];
    final double[] bestHit = new double[2 * NS], bestSpeed = new double[2 * NS], bestCX = new double[2 * NS], bestCY = new double[2 * NS];
    final int[] bestTarget = new int[2 * NS];

    public Physics() {
        Arrays.fill(apart, SEP);
        for (int i = 0; i < N; i++) w[i] = 1 / MASS[i % NP];
    }

    /** Copies the state of another world (for bots that simulate ahead). */
    public void copyFrom(Physics o) {
        System.arraycopy(o.px, 0, px, 0, N); System.arraycopy(o.py, 0, py, 0, N);
        System.arraycopy(o.vx, 0, vx, 0, N); System.arraycopy(o.vy, 0, vy, 0, N);
        System.arraycopy(o.touch, 0, touch, 0, N);
        for (int f = 0; f < 2; f++) {
            System.arraycopy(o.target[f], 0, target[f], 0, NJ); System.arraycopy(o.strength[f], 0, strength[f], 0, NJ);
            hp[f] = o.hp[f]; damage[f] = o.damage[f]; fatigue[f] = o.fatigue[f]; roundLeg[f] = o.roundLeg[f];
            down[f] = o.down[f]; out[f] = o.out[f]; downPt[f] = o.downPt[f]; fallStep[f] = o.fallStep[f]; blockedBy[f] = o.blockedBy[f];
        }
        System.arraycopy(o.apart, 0, apart, 0, apart.length);
        contactSteps = o.contactSteps; stepNo = o.stepNo;
    }

    // ---------- setup
    /** Stands fighter f in the STANCE pose, pelvis at pelvisX millimetres, at rest. */
    public void stand(int f, int pelvisX) {
        int o = f * NP, s = FACING[f];
        for (int i = 0; i < NP; i++) {
            px[o + i] = (pelvisX + s * Moves.STANCE_X[i]) / 1000.0; py[o + i] = Moves.STANCE_Y[i] / 1000.0;
            vx[o + i] = 0; vy[o + i] = 0;
        }
    }

    /** Sets the motors of fighter f: targets in hundredths of a degree, strengths in percent. */
    public void setMotors(int f, int[] t, int[] s) {
        for (int j = 0; j < NJ; j++) { target[f][j] = t[j] / 100.0; strength[f][j] = s[j] / 100.0; }
    }

    /** Current angle of joint j of fighter f, in radians, unwrapped around the middle of its range. */
    public double angle(int f, int j) {
        int o = f * NP;
        double rx = px[o + JB[j]] - px[o + JA[j]], ry = py[o + JB[j]] - py[o + JA[j]];
        double cx = px[o + JC[j]] - px[o + JJ[j]], cy = py[o + JC[j]] - py[o + JJ[j]];
        double d = FACING[f] * JK[j] * atan2(rx * cy - ry * cx, rx * cx + ry * cy) - JOFF[j] - JCEN[j];
        if (d > PI) d -= 2 * PI; else if (d < -PI) d += 2 * PI;
        return JCEN[j] + d;
    }

    /** Speed of joint j of fighter f in degrees per second, measured as the motors measure it. */
    public double angSpeed(int f, int j) {
        int o = f * NP, A = o + JA[j], B = o + JB[j], J = o + JJ[j], C = o + JC[j];
        double rx = px[B] - px[A], ry = py[B] - py[A], cx = px[C] - px[J], cy = py[C] - py[J];
        double wc = (cx * (vy[C] - vy[J]) - cy * (vx[C] - vx[J])) / (cx * cx + cy * cy);
        double wr = (rx * (vy[B] - vy[A]) - ry * (vx[B] - vx[A])) / (rx * rx + ry * ry);
        return FACING[f] * JK[j] * (wc - wr) * RAD2DEG;
    }

    // ---------- forces: each motor puts a torque on its child vector and the opposite torque on its reference vector
    void motors(int f) {
        int o = f * NP, s = FACING[f];
        for (int j = 0; j < NJ; j++) {
            double S = strength[f][j];
            if (S <= 0) continue;
            int A = o + JA[j], B = o + JB[j], J = o + JJ[j], C = o + JC[j], k = JK[j];
            double rx = px[B] - px[A], ry = py[B] - py[A], cx = px[C] - px[J], cy = py[C] - py[J];
            double r2 = rx * rx + ry * ry, c2 = cx * cx + cy * cy;
            double th = angle(f, j);
            double wc = (cx * (vy[C] - vy[J]) - cy * (vx[C] - vx[J])) / c2;
            double wr = (rx * (vy[B] - vy[A]) - ry * (vx[B] - vx[A])) / r2;
            double om = s * k * (wc - wr);                          // speed of the joint angle
            boolean flat = false;
            if (j == LA) flat = wasTouch[o + LHEEL] && wasTouch[o + LTOE];
            else if (j == RA) flat = wasTouch[o + RHEEL] && wasTouch[o + RTOE];
            double tau = KP[j] * (target[f][j] * DEG2RAD - th) - (flat ? KDFLAT[j] : KD[j]) * om;
            double lim = S * TMAX[j] * fatigue[f];
            if (tau * om > 0) lim *= Math.max(0, 1 - Math.abs(om) / WMAX[j]);
            if (tau > lim) tau = lim; else if (tau < -lim) tau = -lim;
            double T = s * k * tau;                                  // world torque on the child vector
            double fcx = -T * cy / c2, fcy = T * cx / c2, frx = T * ry / r2, fry = -T * rx / r2;
            fx[C] += fcx; fy[C] += fcy; fx[J] -= fcx; fy[J] -= fcy;
            fx[B] += frx; fy[B] += fry; fx[A] -= frx; fy[A] -= fry;
        }
    }

    // ---------- constraints
    void bones(int f) {
        int o = f * NP;
        for (int b = 0; b < BONE.length; b++) {
            int a = o + BONE[b][0], c = o + BONE[b][1];
            double dx = px[c] - px[a], dy = py[c] - py[a], d = Math.sqrt(dx * dx + dy * dy);
            double C = (d - BLEN[b]) / (d * (w[a] + w[c]));
            px[a] += dx * C * w[a]; py[a] += dy * C * w[a];
            px[c] -= dx * C * w[c]; py[c] -= dy * C * w[c];
        }
    }

    final int[] lp = new int[4];
    final double[] lgx = new double[4], lgy = new double[4];
    void limits(int f) {
        int o = f * NP, s = FACING[f];
        for (int j = 0; j < NJ; j++) {
            double th = angle(f, j), Cv;
            if (th > JHI[j]) Cv = th - JHI[j]; else if (th < JLO[j]) Cv = th - JLO[j]; else continue;
            int A = o + JA[j], B = o + JB[j], J = o + JJ[j], C = o + JC[j];
            double rx = px[B] - px[A], ry = py[B] - py[A], cx = px[C] - px[J], cy = py[C] - py[J];
            double r2 = rx * rx + ry * ry, c2 = cx * cx + cy * cy, sk = s * JK[j];
            lp[0] = C; lgx[0] = -sk * cy / c2; lgy[0] = sk * cx / c2;
            lp[1] = J; lgx[1] = -lgx[0]; lgy[1] = -lgy[0];
            lp[2] = B; lgx[2] = sk * ry / r2; lgy[2] = -sk * rx / r2;
            lp[3] = A; lgx[3] = -lgx[2]; lgy[3] = -lgy[2];
            for (int i = 1; i < 4; i++) for (int q = 0; q < i; q++) if (lp[q] == lp[i]) { lgx[q] += lgx[i]; lgy[q] += lgy[i]; lgx[i] = 0; lgy[i] = 0; }
            double W = 0;
            for (int i = 0; i < 4; i++) W += w[lp[i]] * (lgx[i] * lgx[i] + lgy[i] * lgy[i]);
            if (W < 1e-12) continue;
            double lam = Cv / W;
            for (int i = 0; i < 4; i++) { px[lp[i]] -= lam * w[lp[i]] * lgx[i]; py[lp[i]] -= lam * w[lp[i]] * lgy[i]; }
        }
    }

    void floor() {
        for (int i = 0; i < N; i++) {
            double r = RADN[i];
            if (py[i] < r) { dep[i] += r - py[i]; py[i] = r; touch[i] = true; }
            if (touch[i]) {                                   // friction, measured from the start of the step
                double lim = MU * dep[i], dx = px[i] - sx[i];
                px[i] = Math.abs(dx) <= lim ? sx[i] : px[i] - Math.signum(dx) * lim;
            }
        }
    }

    // ---------- collisions between the fighters
    // in a side view, legs and feet pass beside the other fighter's legs and feet (a foot still meets a thigh)
    static boolean low(int k) { return k == 5 || k == 6; }
    static boolean leg(int k) { return k == 4 || k == 6 || k == 7; }
    static boolean pass(int a, int b) { return (low(a) && low(b)) || (leg(a) && leg(b)); }
    // depth: each fighter's lead side faces the other's rear side, so a lead limb passes beside the opponent's lead limbs
    public static boolean passShapes(int i, int j) { return pass(SH[i][2], SH[j][2]) || (SIDE[i] != 0 && SIDE[i] == SIDE[j]); }
    // a round kick goes around the body: the kicking leg meets only the head, and the rear forearm and glove while that hand is high
    boolean guardHigh(int f) { return py[f * NP + RHAND] > py[f * NP + NECK] - 0.10; }
    static boolean roundMeets(int b, boolean guard) { return b == 0 || ((b == REAR_FOREARM_SH || b == REAR_GLOVE_SH) && guard); }

    /** True while the lead leg of fighter g overlaps a shape that a round kick passes beside. */
    boolean legInside(int g) {
        int o = g * NP, p = (1 - g) * NP;
        boolean guard = guardHigh(1 - g);
        for (int a = 0; a < NS; a++) if (LEADLEG[a]) for (int b = 0; b < NS; b++) {
            if (PASS[a][b] || roundMeets(b, guard)) continue;
            closest(px, py, o + SH[a][0], o + SH[a][1], p + SH[b][0], p + SH[b][1]);
            double rr = SR[a] + SR[b];
            if (cdx * cdx + cdy * cdy < rr * rr) return true;
        }
        return false;
    }

    boolean boxesOverlap() {
        double ax0 = 1e9, ax1 = -1e9, ay0 = 1e9, ay1 = -1e9, bx0 = 1e9, bx1 = -1e9, by0 = 1e9, by1 = -1e9;
        for (int i = 0; i < NP; i++) {
            ax0 = Math.min(ax0, px[i]); ax1 = Math.max(ax1, px[i]); ay0 = Math.min(ay0, py[i]); ay1 = Math.max(ay1, py[i]);
            int k = NP + i;
            bx0 = Math.min(bx0, px[k]); bx1 = Math.max(bx1, px[k]); by0 = Math.min(by0, py[k]); by1 = Math.max(by1, py[k]);
        }
        double m = 0.32;
        return bx0 - ax1 < m && ax0 - bx1 < m && by0 - ay1 < m && ay0 - by1 < m;
    }

    // the pairs of shapes close enough to meet, listed twice: by fighter 0's shapes first, and by fighter 1's shapes first
    final int[] candI = new int[NS * NS], candJ = new int[NS * NS], cand1I = new int[NS * NS], cand1J = new int[NS * NS];
    final boolean[] candOn = new boolean[NS * NS];
    int nCand;
    final double[] bx0 = new double[2 * NS], bx1 = new double[2 * NS], by0 = new double[2 * NS], by1 = new double[2 * NS];
    void buildCandidates() {
        nCand = 0;
        if (!near) return;
        for (int f = 0; f < 2; f++) for (int i = 0; i < NS; i++) {
            int a = f * NP + SH[i][0], b = f * NP + SH[i][1], q = f * NS + i;
            double r = SR[i] + 0.015;
            bx0[q] = Math.min(px[a], px[b]) - r; bx1[q] = Math.max(px[a], px[b]) + r;
            by0[q] = Math.min(py[a], py[b]) - r; by1[q] = Math.max(py[a], py[b]) + r;
        }
        boolean g0 = guardHigh(0), g1 = guardHigh(1);
        for (int i = 0; i < NS; i++) for (int j = 0; j < NS; j++) {
            int q = NS + j;
            boolean on = !PASS[i][j] && !(roundLeg[0] && LEADLEG[i] && !roundMeets(j, g1)) && !(roundLeg[1] && LEADLEG[j] && !roundMeets(i, g0))
                && bx1[i] >= bx0[q] && bx1[q] >= bx0[i] && by1[i] >= by0[q] && by1[q] >= by0[i];
            candOn[i * NS + j] = on;
            if (on) { candI[nCand] = i; candJ[nCand++] = j; }
        }
        if (nCand == 0) return;
        int m = 0;
        for (int j = 0; j < NS; j++) for (int i = 0; i < NS; i++) if (candOn[i * NS + j]) { cand1I[m] = i; cand1J[m++] = j; }
    }

    double cs, ct, cdx, cdy;   // closest points: parameters on A and B, vector from A to B
    void closest(double[] X, double[] Y, int a0, int a1, int b0, int b1) {
        double p1x = X[a0], p1y = Y[a0], d1x = X[a1] - p1x, d1y = Y[a1] - p1y;
        double p2x = X[b0], p2y = Y[b0], d2x = X[b1] - p2x, d2y = Y[b1] - p2y;
        double rx = p1x - p2x, ry = p1y - p2y;
        double a = d1x * d1x + d1y * d1y, e = d2x * d2x + d2y * d2y, f = d2x * rx + d2y * ry, s, t;
        if (a <= 1e-12 && e <= 1e-12) { s = 0; t = 0; }
        else if (a <= 1e-12) { s = 0; t = clamp01(f / e); }
        else {
            double c = d1x * rx + d1y * ry;
            if (e <= 1e-12) { t = 0; s = clamp01(-c / a); }
            else {
                double b = d1x * d2x + d1y * d2y, den = a * e - b * b;
                s = den > 1e-12 ? clamp01((b * f - c * e) / den) : 0;
                t = (b * s + f) / e;
                if (t < 0) { t = 0; s = clamp01(-c / a); }
                else if (t > 1) { t = 1; s = clamp01((b - c) / a); }
            }
        }
        cs = s; ct = t;
        cdx = p2x + d2x * t - (p1x + d1x * s); cdy = p2y + d2y * t - (p1y + d1y * s);
    }
    /** atan2 written with + - * / only: the same result in every language; error below 1e-7 rad. */
    public static double atan2(double y, double x) {
        double ax = Math.abs(x), ay = Math.abs(y);
        if (ax == 0 && ay == 0) return 0;
        boolean swap = ay > ax;
        double z = swap ? ax / ay : ay / ax, base = 0;
        if (z > 0.41421356237309503) { z = (z - 1) / (z + 1); base = 0.7853981633974483; }
        double z2 = z * z;
        double r = base + z * (1 + z2 * (-1.0 / 3 + z2 * (1.0 / 5 + z2 * (-1.0 / 7 + z2 * (1.0 / 9 + z2 * (-1.0 / 11 + z2 * (1.0 / 13 + z2 * (-1.0 / 15))))))));
        if (swap) r = 1.5707963267948966 - r;
        if (x < 0) r = 3.141592653589793 - r;
        return y < 0 ? -r : r;
    }
    static double clamp01(double x) { return x < 0 ? 0 : x > 1 ? 1 : x; }

    // what a striker may score on: gloves and knees only on the head and torso
    static boolean aims(int striker, int target) { return (striker != 7 && striker != 3) || target <= 1; }

    /** Hits, with the speeds of this step, before the contacts are solved. Nothing scores once a fighter is down. */
    void detectHits() {
        Arrays.fill(touching, false);
        if (nCand > 0 && !down[0] && !down[1]) {
            Arrays.fill(bestHit, 0);
            for (int c = 0; c < nCand; c++) {
                int i = candI[c], j = candJ[c];
                if (STRIKES[i]) strike(0, i, j);
                if (STRIKES[j]) strike(1, j, i);
            }
            for (int q = 0; q < 2 * NS; q++) if (bestHit[q] > 0) {
                int att = q < NS ? 0 : 1, sh = q % NS, dmg = (int) Math.floor(bestHit[q] + 0.5);
                damage[1 - att] += dmg;
                if (record) hitEvents.add(new double[]{stepNo, att, sh, bestTarget[q], bestSpeed[q], dmg, bestCX[q], bestCY[q]});
            }
        }
        for (int q = 0; q < 2 * NS; q++) apart[q] = touching[q] ? 0 : Math.min(apart[q] + 1, SEP);
    }

    /** The striking shape a of fighter g against the shape b of the other fighter. */
    void strike(int g, int a, int b) {
        int ka = KIND[a], kb = KIND[b], o = g * NP, p = (1 - g) * NP, q = g * NS + a;
        int a0 = o + SH[a][0], a1 = o + SH[a][1], b0 = p + SH[b][0], b1 = p + SH[b][1];
        double rr = SR[a] + SR[b];
        closest(px, py, a0, a1, b0, b1);
        double d2 = cdx * cdx + cdy * cdy;
        if (d2 >= rr * rr) return;
        if (kb != 3) touching[q] = true;                      // gloves touching gloves don't count as a contact
        if (apart[q] < SEP) return;                           // one hit per contact
        double s = cs, t = ct;
        if (ka == 5 && py[a0] + s * (py[a1] - py[a0]) < FOOTMIN) return;      // a foot scores only above FOOTMIN: stepping into someone is not a kick
        boolean hit = zone(kb) > 0 && aims(ka, kb), block = kb == 2;         // strikes on the arms are blocked
        if (!hit && !block) return;
        double d = Math.sqrt(d2), nx = d > 1e-9 ? cdx / d : FACING[g], ny = d > 1e-9 ? cdy / d : 0;
        double vax = (1 - s) * vx[a0] + s * vx[a1], vay = (1 - s) * vy[a0] + s * vy[a1];
        double vbx = (1 - t) * vx[b0] + t * vx[b1], vby = (1 - t) * vy[b0] + t * vy[b1];
        double vn = (vax - vbx) * nx + (vay - vby) * ny;      // closing speed
        if (vn < 2) return;
        if (block) {
            blockedBy[g]++;
            if (record) blockEvents.add(new double[]{stepNo, g, px[a0] + s * (px[a1] - px[a0]) + nx * SR[a], py[a0] + s * (py[a1] - py[a0]) + ny * SR[a]});
            return;
        }
        if (vax * nx + vay * ny < STRIKEV) return;            // the striker itself must move toward the target
        double dmg = zone(kb) * (Math.min(vn, 12) - 2);
        if (dmg > bestHit[q]) {
            bestHit[q] = dmg; bestSpeed[q] = vn; bestTarget[q] = b;
            bestCX[q] = px[a0] + s * (px[a1] - px[a0]) + nx * SR[a]; bestCY[q] = py[a0] + s * (py[a1] - py[a0]) + ny * SR[a];
        }
    }

    // The fighters push each other apart. The pairs are solved one after the other, twice from the same positions: once in
    // fighter 0's order and once in fighter 1's, and the two results are averaged, so neither fighter is favoured.
    final double[] qx = new double[N], qy = new double[N];
    void collisions() {
        if (nCand == 0) return;
        System.arraycopy(px, 0, qx, 0, N); System.arraycopy(py, 0, qy, 0, N);
        collide(0);
        for (int i = 0; i < N; i++) { double t = px[i]; px[i] = qx[i]; qx[i] = t; t = py[i]; py[i] = qy[i]; qy[i] = t; }
        collide(1);
        for (int i = 0; i < N; i++) { px[i] = (px[i] + qx[i]) * 0.5; py[i] = (py[i] + qy[i]) * 0.5; }
    }

    void collide(int g) {
        int o = g * NP, p = (1 - g) * NP;
        int[] ca = g == 0 ? candI : cand1J, cb = g == 0 ? candJ : cand1I;
        for (int c = 0; c < nCand; c++) {
            int a = ca[c], b = cb[c];
            if (KIND[a] == 7 || KIND[b] == 7) continue;
            int a0 = o + SH[a][0], a1 = o + SH[a][1], b0 = p + SH[b][0], b1 = p + SH[b][1];
            double rr = SR[a] + SR[b];
            closest(px, py, a0, a1, b0, b1);
            double d2 = cdx * cdx + cdy * cdy;
            if (d2 >= rr * rr) continue;
            double d = Math.sqrt(d2), nx = d > 1e-9 ? cdx / d : FACING[g], ny = d > 1e-9 ? cdy / d : 0, s = cs, t = ct;
            contactNow = true;
            // what overlapped already at the start of the step is only pushed apart by BIAS
            closest(sx, sy, a0, a1, b0, b1);
            double keep = rr - Math.sqrt(cdx * cdx + cdy * cdy) - BIAS, push = keep > 0 ? rr - d - keep : rr - d;
            if (push <= 0) continue;
            double WA = a0 == a1 ? w[a0] : (1 - s) * (1 - s) * w[a0] + s * s * w[a1];
            double WB = b0 == b1 ? w[b0] : (1 - t) * (1 - t) * w[b0] + t * t * w[b1];
            double lam = push / (WA + WB);
            if (a0 == a1) { px[a0] -= nx * lam * w[a0]; py[a0] -= ny * lam * w[a0]; }
            else {
                px[a0] -= nx * lam * (1 - s) * w[a0]; py[a0] -= ny * lam * (1 - s) * w[a0];
                px[a1] -= nx * lam * s * w[a1]; py[a1] -= ny * lam * s * w[a1];
            }
            if (b0 == b1) { px[b0] += nx * lam * w[b0]; py[b0] += ny * lam * w[b0]; }
            else {
                px[b0] += nx * lam * (1 - t) * w[b0]; py[b0] += ny * lam * (1 - t) * w[b0];
                px[b1] += nx * lam * t * w[b1]; py[b1] += ny * lam * t * w[b1];
            }
        }
    }

    // ---------- one step of 2.5 ms
    public void step() {
        for (int i = 0; i < N; i++) { fx[i] = 0; fy[i] = GRAV[i]; wasTouch[i] = touch[i]; touch[i] = false; dep[i] = 0; }
        motors(0); motors(1);
        for (int i = 0; i < N; i++) {
            vx[i] = (vx[i] + fx[i] * w[i] * DT) * DRAG; vy[i] = (vy[i] + fy[i] * w[i] * DT) * DRAG;
            sx[i] = px[i]; sy[i] = py[i];
            px[i] += vx[i] * DT; py[i] += vy[i] * DT;
        }
        near = boxesOverlap();
        buildCandidates();
        detectHits();
        bones(0); bones(1); limits(0); limits(1); collisions(); floor();
        for (int i = 0; i < N; i++) { vx[i] = (px[i] - sx[i]) / DT; vy[i] = (py[i] - sy[i]) / DT; }
        for (int f = 0; f < 2; f++) for (int i = 0; i < NP; i++) {
            int k = f * NP + i;
            if (!touch[k]) continue;
            if (!FOOTPT[i] && !down[f]) { down[f] = true; downPt[f] = i; if (fallStep[f] < 0) fallStep[f] = stepNo; }
            if (Math.abs(px[k]) > RING && !out[f]) { out[f] = true; if (fallStep[f] < 0) fallStep[f] = stepNo; }
        }
        if (contactNow && !down[0] && !down[1]) contactSteps++;         // a contact counts until a fighter is down
        contactNow = false;
        stepNo++;
    }

    /**
     * Starts a turn from the state the bots were given. round0 and round1: whether each fighter asks for a round kick this turn.
     * A lead leg that was passing beside the opponent keeps doing so while it is still inside one of the shapes it passes.
     */
    public void beginTurn(boolean round0, boolean round1) {
        down[0] = down[1] = out[0] = out[1] = false;
        downPt[0] = downPt[1] = -1; fallStep[0] = fallStep[1] = -1;
        blockedBy[0] = blockedBy[1] = 0;
        damage[0] = damage[1] = 0;
        contactSteps = 0; stepNo = 0;
        hitEvents.clear();
        blockEvents.clear();
        for (int i = 0; i < N; i++) touch[i] = py[i] <= RADN[i];
        boolean in0 = !round0 && roundLeg[0] && legInside(0), in1 = !round1 && roundLeg[1] && legInside(1);
        roundLeg[0] = round0 || in0; roundLeg[1] = round1 || in1;
    }

    /** The state the bots are given: whole millimetres and millimetres per second (halves go to the even number). */
    public void roundState() {
        for (int i = 0; i < N; i++) {
            px[i] = Math.rint(px[i] * 1000) / 1000; py[i] = Math.rint(py[i] * 1000) / 1000;
            vx[i] = Math.rint(vx[i] * 1000) / 1000; vy[i] = Math.rint(vy[i] * 1000) / 1000;
        }
    }

    // ---------- centre of mass
    public double comX(int f) { double m = 0, x = 0; for (int i = 0; i < NP; i++) { m += MASS[i]; x += MASS[i] * px[f * NP + i]; } return x / m; }
    public double comY(int f) { double m = 0, y = 0; for (int i = 0; i < NP; i++) { m += MASS[i]; y += MASS[i] * py[f * NP + i]; } return y / m; }
    public double comVX(int f) { double m = 0, v = 0; for (int i = 0; i < NP; i++) { m += MASS[i]; v += MASS[i] * vx[f * NP + i]; } return v / m; }
}
