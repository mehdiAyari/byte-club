<!-- LEAGUES level1 level2 level3 -->
<div id="statement_back" class="statement_back" style="display: none"></div>
<div class="statement-body">

<!-- BEGIN level1 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-right: 15px; margin-left: 15px; margin-bottom: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">This is a league-based game.</p>
  <span class="statement-league-alert-content">
    Several leagues are available. Beating the boss of a league opens the next one, and each league adds new rules.<br><br>
    In this league, the fighters use the 12 ready-made moves.
  </span>
</div>
<!-- END -->
<!-- BEGIN level2 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-right: 15px; margin-left: 15px; margin-bottom: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">Welcome to the second league!</p>
  <span class="statement-league-alert-content">
    New: the <action>JOINTS</action> command drives the 15 joints directly. The ready-made moves are still available.
  </span>
</div>
<!-- END -->
<!-- BEGIN level3 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-right: 15px; margin-left: 15px; margin-bottom: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">Welcome to the third league!</p>
  <span class="statement-league-alert-content">
    New: <strong>stamina</strong>. Every tensed muscle costs stamina, every hit taken drains it, and a tired fighter is weaker.
  </span>
</div>
<!-- END -->

<!-- GOAL -->
<div class="statement-section statement-goal">
  <h1><span class="icon icon-goal">&nbsp;</span><span>The Goal</span></h1>
  <div class="statement-goal-content">
    Two fighters face each other in a ring seen from the side. Each bot drives one of them: a physical body of 19 points moved by 15 joint motors.
    Land strikes, keep the balance and stay in the ring: the winner brings the opponent to <const>0</const> HP, or has more HP after <const>250</const> turns.<br><br>
    Source code of the game (referee, physics engine, viewer): <a href="https://github.com/mehdiAyari/byte-club" target="_blank">github.com/mehdiAyari/byte-club</a>
  </div>
</div>

<!-- RULES -->
<div class="statement-section statement-rules">
  <h1><span class="icon icon-rules">&nbsp;</span><span>Rules</span></h1>
  <div>
    <div class="statement-rules-content">

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">The ring</h3>
<ul>
  <li>Side view, in millimetres. The origin is at the top left of the scene: <var>x</var> grows to the right, <var>y</var> grows downward, and the floor is at <var>y</var> = <const>3000</const>.</li>
  <li>The ring is 6 m wide: from <var>x</var> = <const>2000</const> to <var>x</var> = <const>8000</const>. Its centre is at <var>x</var> = <const>5000</const>.</li>
  <li><strong>Each player sees the match from the left</strong>: in its input, its own fighter stands on the left and faces right. The fighter on the right reads the scene mirrored (<var>x</var> becomes 10000 &minus; <var>x</var>, <var>vx</var> becomes &minus;<var>vx</var>). Commands mean the same on both sides.</li>
  <li>Both fighters start in STANCE, at rest, <var>startDistance</var> apart, pelvis to pelvis (<const>1600</const> to <const>2400</const>, an even number, drawn at random), on either side of the centre. A fighter faces the same way for the whole match.</li>
  <li>Each fighter starts with <const>100</const> HP.</li>
</ul>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">The fighter</h3>
<p>A fighter is 19 points, 65.4 kg in all, joined by 20 rigid bones. Both fighters stand as two right-handed boxers face to face: LEAD is the side in front, REAR the other one.</p>
<div style="text-align: center; margin: 12px 0"><img src="https://cdn-games.codingame.com/community/7212247-1794655631881/1bafa0923b79d39e3899b7ef88c461b9426426631168339d19f95097c718acb0.png" alt="The 19 points of a fighter, numbered, lead side in blue, rear side in orange" style="max-width: 100%; border-radius: 8px" /></div>
<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 8px">#</th><th style="padding: 2px 8px">Point</th><th style="padding: 2px 8px">Mass (kg)</th><th style="padding: 2px 8px">Radius (mm)</th><th style="padding: 2px 8px">Joint that moves it</th></tr>
  <tr><td style="padding: 2px 8px">0</td><td style="padding: 2px 8px">HEAD</td><td style="padding: 2px 8px">5</td><td style="padding: 2px 8px">110</td><td style="padding: 2px 8px">0 NECK</td></tr>
  <tr><td style="padding: 2px 8px">1</td><td style="padding: 2px 8px">NECK (top of the spine, between the shoulders)</td><td style="padding: 2px 8px">6</td><td style="padding: 2px 8px">120</td><td style="padding: 2px 8px">1 UPPER_BACK</td></tr>
  <tr><td style="padding: 2px 8px">2</td><td style="padding: 2px 8px">CHEST</td><td style="padding: 2px 8px">9</td><td style="padding: 2px 8px">130</td><td style="padding: 2px 8px">2 LOWER_BACK</td></tr>
  <tr><td style="padding: 2px 8px">3</td><td style="padding: 2px 8px">WAIST</td><td style="padding: 2px 8px">9</td><td style="padding: 2px 8px">120</td><td style="padding: 2px 8px">none</td></tr>
  <tr><td style="padding: 2px 8px">4</td><td style="padding: 2px 8px">PELVIS (the hips)</td><td style="padding: 2px 8px">12</td><td style="padding: 2px 8px">130</td><td style="padding: 2px 8px">none</td></tr>
  <tr><td style="padding: 2px 8px">5, 8</td><td style="padding: 2px 8px">LEAD_SHOULDER, REAR_SHOULDER</td><td style="padding: 2px 8px">2</td><td style="padding: 2px 8px">60</td><td style="padding: 2px 8px">3 LEAD_GIRDLE, 6 REAR_GIRDLE</td></tr>
  <tr><td style="padding: 2px 8px">6, 9</td><td style="padding: 2px 8px">LEAD_ELBOW, REAR_ELBOW</td><td style="padding: 2px 8px">1.5</td><td style="padding: 2px 8px">50</td><td style="padding: 2px 8px">4 LEAD_SHOULDER, 7 REAR_SHOULDER</td></tr>
  <tr><td style="padding: 2px 8px">7, 10</td><td style="padding: 2px 8px">LEAD_HAND, REAR_HAND</td><td style="padding: 2px 8px">1.2</td><td style="padding: 2px 8px">65</td><td style="padding: 2px 8px">5 LEAD_ELBOW, 8 REAR_ELBOW</td></tr>
  <tr><td style="padding: 2px 8px">11, 15</td><td style="padding: 2px 8px">LEAD_KNEE, REAR_KNEE</td><td style="padding: 2px 8px">4</td><td style="padding: 2px 8px">80</td><td style="padding: 2px 8px">9 LEAD_HIP, 12 REAR_HIP</td></tr>
  <tr><td style="padding: 2px 8px">12, 16</td><td style="padding: 2px 8px">LEAD_ANKLE, REAR_ANKLE</td><td style="padding: 2px 8px">2</td><td style="padding: 2px 8px">40</td><td style="padding: 2px 8px">10 LEAD_KNEE, 13 REAR_KNEE</td></tr>
  <tr><td style="padding: 2px 8px">13, 17</td><td style="padding: 2px 8px">LEAD_HEEL, REAR_HEEL</td><td style="padding: 2px 8px">0.5</td><td style="padding: 2px 8px">30</td><td style="padding: 2px 8px">none</td></tr>
  <tr><td style="padding: 2px 8px">14, 18</td><td style="padding: 2px 8px">LEAD_TOES, REAR_TOES</td><td style="padding: 2px 8px">1</td><td style="padding: 2px 8px">30</td><td style="padding: 2px 8px">11 LEAD_ANKLE, 14 REAR_ANKLE</td></tr>
</table>
<ul>
  <li>Bones, in mm: NECK&ndash;HEAD <const>200</const>, CHEST&ndash;NECK <const>180</const>, WAIST&ndash;CHEST <const>180</const>, PELVIS&ndash;WAIST <const>140</const>; for each arm NECK&ndash;SHOULDER <const>130</const>, SHOULDER&ndash;ELBOW <const>300</const>, ELBOW&ndash;HAND <const>280</const>; for each leg PELVIS&ndash;KNEE <const>450</const>, KNEE&ndash;ANKLE <const>450</const>, and the foot, a rigid triangle: the heel 50 mm behind and 60 mm below the ankle, the toes 170 mm in front and 60 mm below it.</li>
  <li>A point touches the floor when it is closer to it than its radius. Standing flat, the heels and toes are 30 mm above the floor (<var>y</var> = <const>2970</const>).</li>
  <li>Against the other fighter, a body is made of round shapes: the head (radius 110), the torso (capsules NECK&ndash;CHEST, CHEST&ndash;WAIST, WAIST&ndash;PELVIS of radius 130, 120, 130), the upper arms and forearms (50 and 45), the gloves (circles of radius 65 around the hands), the thighs and shins (80 and 50), the feet (capsules HEEL&ndash;TOES of radius 30) and the knees (circles of radius 75, which only strike). A fighter never collides with itself.</li>
</ul>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Joints and motors</h3>
<p>15 joints, each driven by a motor, in this order:</p>
<div style="text-align: center; margin: 12px 0"><img src="https://cdn-games.codingame.com/community/7212247-1794655631881/0745a659ab3c2e60401cfd91a2878d5286351d4d784803cd0230481ed724712c.png" alt="Where 0 degrees is and which way is positive for each joint" style="max-width: 100%; border-radius: 8px" /></div>
<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 6px">#</th><th style="padding: 2px 6px">Joint</th><th style="padding: 2px 6px">0&deg;</th><th style="padding: 2px 6px">Positive angles</th><th style="padding: 2px 6px">Range (&deg;)</th><th style="padding: 2px 6px">Max torque (N&middot;m)</th><th style="padding: 2px 6px">Max speed (rad/s)</th></tr>
  <tr><td style="padding: 2px 6px">0</td><td style="padding: 2px 6px">NECK</td><td style="padding: 2px 6px">head in line with the upper back</td><td style="padding: 2px 6px">head forward, chin tucked</td><td style="padding: 2px 6px">-30 to 45</td><td style="padding: 2px 6px">40</td><td style="padding: 2px 6px">10</td></tr>
  <tr><td style="padding: 2px 6px">1</td><td style="padding: 2px 6px">UPPER_BACK</td><td style="padding: 2px 6px">straight</td><td style="padding: 2px 6px">bent forward</td><td style="padding: 2px 6px">-20 to 35</td><td style="padding: 2px 6px">120</td><td style="padding: 2px 6px">10</td></tr>
  <tr><td style="padding: 2px 6px">2</td><td style="padding: 2px 6px">LOWER_BACK</td><td style="padding: 2px 6px">straight</td><td style="padding: 2px 6px">bent forward</td><td style="padding: 2px 6px">-25 to 40</td><td style="padding: 2px 6px">150</td><td style="padding: 2px 6px">10</td></tr>
  <tr><td style="padding: 2px 6px">3, 6</td><td style="padding: 2px 6px">LEAD_GIRDLE, REAR_GIRDLE</td><td style="padding: 2px 6px">shoulder on the torso line, under the neck</td><td style="padding: 2px 6px">shoulder forward</td><td style="padding: 2px 6px">-60 to 60</td><td style="padding: 2px 6px">120</td><td style="padding: 2px 6px">15</td></tr>
  <tr><td style="padding: 2px 6px">4, 7</td><td style="padding: 2px 6px">LEAD_SHOULDER, REAR_SHOULDER</td><td style="padding: 2px 6px">arm hanging along the torso</td><td style="padding: 2px 6px">arm raised to the front</td><td style="padding: 2px 6px">-60 to 180</td><td style="padding: 2px 6px">80</td><td style="padding: 2px 6px">40</td></tr>
  <tr><td style="padding: 2px 6px">5, 8</td><td style="padding: 2px 6px">LEAD_ELBOW, REAR_ELBOW</td><td style="padding: 2px 6px">arm straight</td><td style="padding: 2px 6px">elbow bent</td><td style="padding: 2px 6px">0 to 150</td><td style="padding: 2px 6px">60</td><td style="padding: 2px 6px">60</td></tr>
  <tr><td style="padding: 2px 6px">9, 12</td><td style="padding: 2px 6px">LEAD_HIP, REAR_HIP</td><td style="padding: 2px 6px">thigh in line with the torso</td><td style="padding: 2px 6px">thigh raised to the front</td><td style="padding: 2px 6px">-45 to 130</td><td style="padding: 2px 6px">220</td><td style="padding: 2px 6px">25</td></tr>
  <tr><td style="padding: 2px 6px">10, 13</td><td style="padding: 2px 6px">LEAD_KNEE, REAR_KNEE</td><td style="padding: 2px 6px">leg straight</td><td style="padding: 2px 6px">knee bent</td><td style="padding: 2px 6px">0 to 150</td><td style="padding: 2px 6px">220</td><td style="padding: 2px 6px">50</td></tr>
  <tr><td style="padding: 2px 6px">11, 14</td><td style="padding: 2px 6px">LEAD_ANKLE, REAR_ANKLE</td><td style="padding: 2px 6px">sole at a right angle to the shin</td><td style="padding: 2px 6px">toes pointed down</td><td style="padding: 2px 6px">-40 to 50</td><td style="padding: 2px 6px">150</td><td style="padding: 2px 6px">20</td></tr>
</table>
<ul>
  <li>Girdles, shoulders and hips are measured from the torso line (neck to pelvis), every other joint from the bone before it, in each fighter's own frame: the same command makes the same move on both sides.</li>
  <li>Each motor pulls its joint toward its <var>target</var> angle with a torque capped at <var>strength</var> (<const>0</const> to <const>100</const> %) &times; max torque, the full cap 10&deg; away from the target (5&deg; for the ankles). Like a muscle, it gets weaker as it moves faster, down to 0 at the max speed. Strength 0 leaves a joint limp; a range is a hard stop; targets and strengths stay until they are changed.</li>
</ul>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">A turn</h3>
<ol>
  <li>Both bots send a command at the same time.</li>
  <li>The game plays 0.1 s of physics in 40 steps of 2.5 ms. At every step the bodies move and push each other, and the strikes that land are found.</li>
  <li>At the end of the turn, everything is settled: the HP taken by the strikes, the stamina, the falls and ring-outs, and the end of the match.</li>
</ol>
<p>A match lasts at most <const>250</const> turns: 25 seconds of fighting.</p>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Commands</h3>
<p>Each turn, a bot writes one line with one of these commands:</p>
<ul>
  <li><strong>A move</strong> from the table below, one frame per turn. A held pose (STANCE, GUARD, RELAX, DUCK) lasts as long as it is sent. A timed move lasts the number of turns given: sending its name again, or <action>WAIT</action>, lets it go on; any other command interrupts it at once. At its end, the fighter is back in STANCE, and sending its name again starts it over.</li>
  <li><action>WAIT</action>: a running move goes on and a held pose is held.
<!-- BEGIN level2 level3 -->
  After <action>JOINTS</action>, the motors keep their targets and strengths.
<!-- END -->
  </li>
</ul>
<!-- BEGIN level2 -->
<div class="statement-new-league-rule">
<!-- END -->
<!-- BEGIN level2 level3 -->
<ul>
  <li><action>JOINTS t1 s1 t2 s2 ... t15 s15</action> sets the 15 motors directly, in the joint order: a target in degrees and a strength in % for each joint.
    <ul>
      <li>Each value is a number (an optional minus sign, up to 6 digits, optionally a point and up to 6 decimals), or <action>*</action> to keep the current value. A target is rounded to 0.01&deg;, a strength to a whole number.</li>
      <li><action>ROUND</action> (in capitals) after the 30 values swings the lead leg around the opponent's body for this turn, as HIGH_KICK does (see Strikes).</li>
      <li><action>JOINTS</action> stops the running move. The legs then only follow their targets: the engine no longer places the feet nor keeps the balance.</li>
      <li>Example: <action>JOINTS</action>, 14 <action>*</action>, <action>10 100</action>, 14 <action>*</action> drops the rear arm (joint 7 to 10&deg; at 100 %) and keeps every other motor as it is.</li>
    </ul>
  </li>
</ul>
<!-- END -->
<!-- BEGIN level2 -->
</div>
<!-- END -->
<p>Any text after the command is a message, shown above the fighter (40 characters at most).
<!-- BEGIN level2 level3 -->
After <action>JOINTS</action>, the message must not start with a number or <action>*</action>.
<!-- END -->
</p>
<p>The line may start with <action>LOOK skin hair</action> to choose the fighter's look on the first turn (example: <action>LOOK 3 5 STEP_FORWARD</action>; alone, it is a <action>WAIT</action>). <var>skin</var>: <const>0</const> (very light) to <const>7</const> (very dark). <var>hair</var>: <const>0</const> black, <const>1</const> dark brown, <const>2</const> brown, <const>3</const> light brown, <const>4</const> dark blond, <const>5</const> blond, <const>6</const> auburn, <const>7</const> ginger, <const>8</const> grey, <const>9</const> bald. Shorts and gloves wear the player's colour.</p>
<p><strong>Wrong lines.</strong> A line that is not a command (empty, or an unknown name: names are in capitals) loses the match. A command that is recognized but cannot be played as written is not played: the console says why, and WAIT is played instead
<!-- BEGIN level1 -->
(for example <action>JOINTS</action>, which is not available in this league).
<!-- END -->
<!-- BEGIN level2 level3 -->
(for example <action>JOINTS</action> with a value missing, one too many, or a value that is not a number). A value out of its range is played as the closest valid value, and the console says so.
<!-- END -->
A <action>LOOK</action> that cannot be used (a later turn, other values) is ignored, with a message, and the rest of the line is played.</p>

<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 6px">Move</th><th style="padding: 2px 6px">Turns</th><th style="padding: 2px 6px">What it does</th></tr>
  <tr><td style="padding: 2px 6px">STANCE</td><td style="padding: 2px 6px">held</td><td style="padding: 2px 6px">boxing stance: lead foot forward, rear foot 42 cm behind it, knees slightly bent, lead glove at eye level, rear glove at the chin</td></tr>
  <tr><td style="padding: 2px 6px">GUARD</td><td style="padding: 2px 6px">held</td><td style="padding: 2px 6px">high guard: both gloves in front of the face, arms held firmer, chin tucked</td></tr>
  <tr><td style="padding: 2px 6px">RELAX</td><td style="padding: 2px 6px">held</td><td style="padding: 2px 6px">arms hanging at 10 % strength: cheaper in stamina, but the head is open</td></tr>
  <tr><td style="padding: 2px 6px">DUCK</td><td style="padding: 2px 6px">held</td><td style="padding: 2px 6px">back bent forward, chin down, gloves up, a quarter of the way down per turn: fully ducked after 4 turns, the head about 35 cm lower</td></tr>
  <tr><td style="padding: 2px 6px">JAB</td><td style="padding: 2px 6px">3</td><td style="padding: 2px 6px">lead straight in 1 turn, the pelvis 11 cm forward over the feet; then 2 turns back to the stance</td></tr>
  <tr><td style="padding: 2px 6px">CROSS</td><td style="padding: 2px 6px">3</td><td style="padding: 2px 6px">rear straight in 2 turns, the pelvis 9 cm forward; then 1 turn back</td></tr>
  <tr><td style="padding: 2px 6px">OVERHAND</td><td style="padding: 2px 6px">3</td><td style="padding: 2px 6px">the rear arm loads high (1 turn), then comes down over the opponent's gloves (1 turn); then 1 turn back</td></tr>
  <tr><td style="padding: 2px 6px">KICK</td><td style="padding: 2px 6px">6</td><td style="padding: 2px 6px">lead-leg kick: weight onto the rear foot, knee up, kick, knee back, foot down, on one leg meanwhile</td></tr>
  <tr><td style="padding: 2px 6px">HIGH_KICK</td><td style="padding: 2px 6px">12</td><td style="padding: 2px 6px">lead-leg round kick to the head: the leg swings up and around the opponent's body, then comes back down</td></tr>
  <tr><td style="padding: 2px 6px">STEP_FORWARD</td><td style="padding: 2px 6px">7</td><td style="padding: 2px 6px">the lead foot goes about 25 cm forward, then the rear foot follows it</td></tr>
  <tr><td style="padding: 2px 6px">STEP_BACK</td><td style="padding: 2px 6px">7</td><td style="padding: 2px 6px">the rear foot goes first, then the lead foot follows it</td></tr>
  <tr><td style="padding: 2px 6px">JUMP</td><td style="padding: 2px 6px">7</td><td style="padding: 2px 6px">crouch, push off with both legs, knees tucked in the air, landing: the feet leave the floor by about 30 cm</td></tr>
</table>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Legs and balance</h3>
<p>A fighter keeps its balance while its <strong>weight point</strong> stays over its feet: the <var>x</var> of its centre of mass plus the horizontal speed of the centre of mass &times; &radic;(height of the centre of mass above the floor &divide; 9.81), in m and m/s.</p>
<div style="text-align: center; margin: 12px 0"><img src="https://cdn-games.codingame.com/community/7212247-1794655631881/23dc2f098bba196443e0fb3b604aa0c36546e859661c21dc8920db0e5d549d55.png" alt="Strike zones, and the weight point against the feet" style="max-width: 100%; border-radius: 8px" /></div>
<ul>
  <li>For the held poses, the punches and the steps, <strong>the engine runs the legs</strong> at 100 % strength: it places the pelvis above the feet, the ankles lean against where the weight goes, and the feet are put back 42 cm apart when they drift.</li>
  <li><strong>Balance step</strong>: when the weight point goes more than 2 cm past the front toes or behind the rear heel, the foot on that side steps under it. The game summary reports these steps. During STEP_FORWARD and STEP_BACK the feet only go the way of the step: a step back while the weight goes forward (or the reverse) can make the fighter fall.</li>
  <li>KICK, HIGH_KICK and JUMP leave the legs to their own targets: no help with the balance.
<!-- BEGIN level2 level3 -->
  So does <action>JOINTS</action>.
<!-- END -->
  </li>
  <li><var>rise</var> (<const>0</const> to <const>4</const>) counts how many quarters deep the fighter is ducked: DUCK adds one, the other poses and the punches take one away, and a kick, a step or JUMP
<!-- BEGIN level2 level3 -->
  (or <action>JOINTS</action>)
<!-- END -->
  sets it back to <const>0</const>.</li>
</ul>
<p>The exact numbers of the legs are in the source code (<var>Game.java</var>).</p>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Strikes</h3>
<ul>
  <li>Gloves, knees and feet strike. Gloves and knees score on the head and the torso; feet also score on the thighs, shins and feet. Zone: head <const>2</const>, torso <const>1</const>, legs <const>0.5</const>.</li>
  <li>A strike scores when the striking part touches the opponent while the two close in at <const>2</const> m/s or more, and the striking part itself moves toward the target at <const>1</const> m/s or more. <strong>Damage = zone &times; (closing speed &minus; 2)</strong>, with the closing speed in m/s capped at <const>12</const>, rounded to the nearest whole number (halves up). Example: a glove landing on the head at 7 m/s takes 10 HP.</li>
  <li>A strike on an upper arm or a forearm is blocked: no damage. Touching a glove deals nothing either.</li>
  <li>One hit per contact: a striking part scores again only after it has been away from the opponent for 10 physics steps (25 ms). A foot scores only where it touches at least 30 cm above the floor. Nothing scores once a fighter is down.</li>
  <li><strong>Depth</strong>: each fighter's lead side faces the other's rear side. A lead limb passes beside the opponent's lead limbs and meets its rear limbs, head and torso; a rear limb meets the opponent's lead limbs, head and torso. So a JAB can only be blocked by the rear arm, a CROSS by the lead arm. The legs pass beside each other, except that a lead foot meets the rear thigh.</li>
  <li><strong>Round kick</strong>: during HIGH_KICK
<!-- BEGIN level2 level3 -->
  (or a turn of <action>JOINTS ... ROUND</action>)
<!-- END -->
  the lead leg goes around the opponent's body: it meets only the head, and the rear forearm and glove while that glove is held high.</li>
  <li>Every contact also pushes: the two bodies never overlap, and a blocked strike can still knock a fighter off balance.</li>
</ul>
<p>The strikes against a fighter holding a pose, 0.8 to 0.9 m away (pelvis to pelvis), measured with the referee:</p>
<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 6px">Strike</th><th style="padding: 2px 6px">on STANCE</th><th style="padding: 2px 6px">on GUARD</th><th style="padding: 2px 6px">on DUCK</th></tr>
  <tr><td style="padding: 2px 6px">JAB</td><td style="padding: 2px 6px">2 to 3 HP (head)</td><td style="padding: 2px 6px">0</td><td style="padding: 2px 6px">0</td></tr>
  <tr><td style="padding: 2px 6px">CROSS</td><td style="padding: 2px 6px">4 to 7 HP (head)</td><td style="padding: 2px 6px">1 to 2 HP</td><td style="padding: 2px 6px">0</td></tr>
  <tr><td style="padding: 2px 6px">OVERHAND</td><td style="padding: 2px 6px">8 to 10 HP (head)</td><td style="padding: 2px 6px">6 to 7 HP</td><td style="padding: 2px 6px">0</td></tr>
  <tr><td style="padding: 2px 6px">KICK</td><td style="padding: 2px 6px">5 to 6 HP (thigh, two hits)</td><td style="padding: 2px 6px">4 to 6 HP</td><td style="padding: 2px 6px">4 to 5 HP</td></tr>
  <tr><td style="padding: 2px 6px">HIGH_KICK</td><td style="padding: 2px 6px">5 to 7 HP (head) from 0.85 m; less closer</td><td style="padding: 2px 6px">11 to 12 HP (head): it goes around the guard</td><td style="padding: 2px 6px">0</td></tr>
</table>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Falls and ring-outs</h3>
<ul>
  <li>Only the feet (ankles, heels and toes) may touch the floor. When any other point touches it, the fighter is <strong>down</strong>. When any point touches the floor outside the ring (<var>x</var> below <const>2000</const> or above <const>8000</const>), the fighter is <strong>out</strong>.</li>
  <li>A down or an out costs <const>20</const> HP, except after a push: when the fighters touched during that turn or the 20 turns before, and no strike took HP in that time, the fall is free.</li>
  <li>After a turn with a down or an out, both fighters go back to their starting places, in STANCE and at rest. HP and stamina stay as they are.</li>
</ul>

<!-- BEGIN level1 level2 -->
<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Stamina</h3>
<p>Stamina is not used in this league: it always reads <const>100000</const> (100 %).</p>
<!-- END -->
<!-- BEGIN level3 -->
<div class="statement-new-league-rule">
<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Stamina</h3>
<ul>
  <li>Stamina is counted in thousandths of a percent: <const>100000</const> is 100 %, the start and the most.</li>
  <li>Each turn, before its physics, a fighter gets back 3.5 % and pays for the tension of its motors: each joint costs its strength (in %) &times; 0.002 % for the neck and the backs, 0.003 % for the girdles, 0.004 % for the other joints. A move costs the strengths of its own frame.</li>
  <li>A strike that takes <var>n</var> HP also takes <var>n</var> % of stamina, at the end of the turn.</li>
  <li>A tired fighter is weaker: the torque cap of every motor is multiplied by 0.4 + 0.6 &times; stamina &divide; 100 %.</li>
</ul>
<p>Stamina won (+) or spent (&minus;) over a whole move, turn regain included, when no hit lands and the move starts at 98 % or less:</p>
<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 6px">Move</th><th style="padding: 2px 6px">Stamina</th><th style="padding: 2px 6px">Move</th><th style="padding: 2px 6px">Stamina</th></tr>
  <tr><td style="padding: 2px 6px">STANCE</td><td style="padding: 2px 6px">+0.24 % per turn</td><td style="padding: 2px 6px">OVERHAND (3 turns)</td><td style="padding: 2px 6px">&minus;2.12 %</td></tr>
  <tr><td style="padding: 2px 6px">GUARD</td><td style="padding: 2px 6px">&minus;0.40 % per turn</td><td style="padding: 2px 6px">KICK (6 turns)</td><td style="padding: 2px 6px">&minus;1.30 %</td></tr>
  <tr><td style="padding: 2px 6px">RELAX</td><td style="padding: 2px 6px">+0.72 % per turn</td><td style="padding: 2px 6px">HIGH_KICK (12 turns)</td><td style="padding: 2px 6px">&minus;1.00 %</td></tr>
  <tr><td style="padding: 2px 6px">DUCK</td><td style="padding: 2px 6px">&minus;0.40 % per turn</td><td style="padding: 2px 6px">STEP_FORWARD (7 turns)</td><td style="padding: 2px 6px">&minus;0.84 %</td></tr>
  <tr><td style="padding: 2px 6px">JAB (3 turns)</td><td style="padding: 2px 6px">&minus;0.70 %</td><td style="padding: 2px 6px">STEP_BACK (7 turns)</td><td style="padding: 2px 6px">&minus;0.50 %</td></tr>
  <tr><td style="padding: 2px 6px">CROSS (3 turns)</td><td style="padding: 2px 6px">&minus;2.12 %</td><td style="padding: 2px 6px">JUMP (7 turns)</td><td style="padding: 2px 6px">+1.13 %</td></tr>
</table>
<p>Stamina never goes over 100 %: what a turn would add above it is lost. A JUMP started at 100 % ends at 100 %, and a HIGH_KICK started at 100 % costs 1.60 %.</p>
</div>
<!-- END -->

    </div>
  </div>
</div>

<!-- Victory conditions -->
<div class="statement-victory-conditions">
  <div class="icon victory"></div>
  <div class="blk">
    <div class="title">Victory Conditions</div>
    <div class="text">
      <ul style="padding-top: 0; padding-bottom: 0;">
        <li>Bring the opponent to <const>0</const> HP or less (a knockout).</li>
        <li>Have more HP than the opponent after <const>250</const> turns.</li>
        <li>Have more HP than the opponent when the fighters have not touched for <const>150</const> turns: the match stops there.</li>
      </ul>
      The same HP at the end, or both fighters at 0 HP in the same turn, is a draw.
    </div>
  </div>
</div>
<!-- Lose conditions -->
<div class="statement-lose-conditions">
  <div class="icon lose"></div>
  <div class="blk">
    <div class="title">Loss Conditions</div>
    <div class="text">
      <ul style="padding-top: 0; padding-bottom: 0;">
        <li>Reaching <const>0</const> HP, or having fewer HP than the opponent when the match ends.</li>
        <li>Writing a line that is not a command, or not answering in time: the match is lost at once (a draw if both players do it in the same turn).</li>
      </ul>
    </div>
  </div>
</div>

<!-- EXPERT RULES -->
<div class="statement-section statement-expertrules">
  <h1><span class="icon icon-expertrules">&nbsp;</span><span>Expert Rules</span></h1>
  <div class="statement-expert-rules-content">
    <ul>
      <li><strong>The input is the whole state of the match.</strong> The engine is on <a href="https://github.com/mehdiAyari/byte-club" target="_blank">GitHub</a> (classes <var>Game</var>, <var>Physics</var>, <var>Moves</var> and <var>Command</var>). A copy rebuilt with <var>Game.fromInput</var> holds the player's fighter as fighter 0, on the left, as the input shows it: given both commands (<var>apply(0, ...)</var> for the player, <var>apply(1, ...)</var> for the opponent), it computes exactly the next input, <var>inputLines(0)</var>. Only the opponent's command is unknown.</li>
      <li>Nothing is random during a match. Only the starting distance is drawn; in the IDE, the game parameter <var>distance</var> sets it.</li>
      <li>A physics step only uses additions, subtractions, multiplications, divisions and square roots on 64-bit floating-point numbers: it gives the same numbers in every language.</li>
      <li>A step of 2.5 ms: gravity and the motors change the speeds, the points move, the strikes are found (their HP are taken at the end of the turn), then the bones, the joint ranges, the contacts and the floor are enforced. The contacts between the fighters are solved in both orders and averaged: a match and its mirror image play the same. The exact order and the motor law are in <var>Physics.java</var>.</li>
      <li>At the end of a turn, positions are rounded to the millimetre and speeds to the millimetre per second (halves to the even number): the next turn starts from exactly what the input says. The angles of the input are worked out from the points and rounded, the targets are exact; a joint's speed can be worked out from the speeds of the points (<var>Physics.angSpeed</var>).</li>
    </ul>
  </div>
</div>

<!-- PROTOCOL -->
<div class="statement-section statement-protocol">
  <h1><span class="icon icon-protocol">&nbsp;</span><span>Game Input</span></h1>
  <div class="blk">
    <div class="title">Initialization input</div>
    <div class="text">
      <span class="statement-lineno">First line:</span> one integer <var>startDistance</var>: the distance between the two pelvises at the start, in mm.
    </div>
  </div>
  <div class="blk">
    <div class="title">Input for one game turn</div>
    <div class="text">
      4 lines. The player's own fighter always comes first, and is seen on the left (see The ring): <var>x</var> grows to the right and <var>y</var> downward, in mm and mm/s.<br>
      <span class="statement-lineno">First 2 lines:</span> 6 space-separated values <var>hp</var> <var>stamina</var> <var>moveName</var> <var>frame</var> <var>rise</var> <var>roundKick</var> (<var>moveName</var> is a string, the others are integers), for the player's fighter, then for the opponent: HP; stamina in thousandths of a percent (<const>0</const> to <const>100000</const>); the move the fighter is in, and how many of its turns have been played (<const>0</const> for a held pose);
<!-- BEGIN level2 level3 -->
      after a <action>JOINTS</action> command, the move is <action>JOINTS</action> and the frame <const>0</const>;
<!-- END -->
      how deep the fighter is ducked (<const>0</const> to <const>4</const> quarters); <const>1</const> when its lead leg went around the other body last turn (round kick), else <const>0</const>.<br>
      <span class="statement-lineno">Next line:</span> 304 space-separated values: 8 values <var>x</var> <var>y</var> <var>vx</var> <var>vy</var> <var>apart</var> <var>angle</var> <var>target</var> <var>strength</var> for each of the 38 points (<var>angle</var> and <var>target</var> are decimal numbers, the others are integers): the player's 19 points, then the opponent's, in the order of the points table.
      <ul>
        <li><var>x</var> <var>y</var> <var>vx</var> <var>vy</var>: position and speed of the point. For example, the distance between the two pelvises is the <var>x</var> of the opponent's PELVIS (point 23 of the line) minus the <var>x</var> of the player's (point 4).</li>
        <li><var>apart</var>: the physics steps since the striking part at this point last touched the other fighter (<const>0</const> to <const>10</const>; it can score again at <const>10</const>). The gloves are at the hands, the feet at the toes, the knees at the knees; <const>10</const> for the points that do not strike.</li>
        <li><var>angle</var> <var>target</var> <var>strength</var>: the motor of the joint that moves this point (see the points table), as it is now, legs run by the engine included: angle and target in degrees (2 decimals), strength in %. <const>0.00 0.00 0</const> for WAIST, PELVIS and the heels, which no joint moves. Read in order without these four points, the points give the joints in the joint order.</li>
      </ul>
      <span class="statement-lineno">Last line:</span> 2 space-separated integers <var>sinceContact</var> <var>sinceHit</var>: turns since the fighters last touched, and since a strike last took HP (<const>0</const>: during the last turn). Both are <const>21</const> at the start and after a fall; <var>sinceContact</var> counts up to <const>150</const>, <var>sinceHit</var> up to <const>21</const>.
    </div>
  </div>
  <div class="blk">
    <div class="title">Output for one game turn</div>
    <div class="text">
      <span class="statement-lineno">One line:</span> a command (see Commands), optionally followed by a message.
    </div>
  </div>
  <div class="blk">
    <div class="title">Constraints</div>
    <div class="text">
      <const>1600</const> &le; <var>startDistance</var> &le; <const>2400</const><br>
      Response time for the first turn &le; <const>1000</const> ms<br>
      Response time per turn &le; <const>50</const> ms<br>
      At most <const>250</const> turns
    </div>
  </div>
</div>
<!-- BEGIN level1 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-top: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">What is in store for me in the higher leagues?</p>
  The extra rules available in higher leagues are:
  <ul>
    <li>The <action>JOINTS</action> command: a bot drives the 15 joints itself, with a target angle and a strength for each joint.</li>
    <li><strong>Stamina</strong>: every tensed muscle costs stamina, every hit taken drains it, and a tired fighter is weaker.</li>
  </ul>
</div>
<!-- END -->
<!-- BEGIN level2 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-top: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">What is in store for me in the higher leagues?</p>
  The extra rule available in the next league is:
  <ul>
    <li><strong>Stamina</strong>: every tensed muscle costs stamina, every hit taken drains it, and a tired fighter is weaker.</li>
  </ul>
</div>
<!-- END -->
</div>
