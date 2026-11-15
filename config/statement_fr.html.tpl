<!-- LEAGUES level1 level2 level3 -->
<div id="statement_back" class="statement_back" style="display: none"></div>
<div class="statement-body">

<!-- BEGIN level1 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-right: 15px; margin-left: 15px; margin-bottom: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">Ce jeu est basé sur un système de ligues.</p>
  <span class="statement-league-alert-content">
    Plusieurs ligues sont disponibles. Battre le boss d'une ligue ouvre la suivante, et chaque ligue ajoute de nouvelles règles.<br><br>
    Dans cette ligue, les combattants utilisent les 12 mouvements tout faits.
  </span>
</div>
<!-- END -->
<!-- BEGIN level2 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-right: 15px; margin-left: 15px; margin-bottom: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">Bienvenue dans la deuxième ligue !</p>
  <span class="statement-league-alert-content">
    Nouveau : la commande <action>JOINTS</action> pilote directement les 15 articulations. Les mouvements tout faits restent disponibles.
  </span>
</div>
<!-- END -->
<!-- BEGIN level3 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-right: 15px; margin-left: 15px; margin-bottom: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">Bienvenue dans la troisième ligue !</p>
  <span class="statement-league-alert-content">
    Nouveau : <strong>l'endurance</strong>. Chaque muscle contracté coûte de l'endurance, chaque coup reçu en retire, et un combattant fatigué est plus faible.
  </span>
</div>
<!-- END -->

<!-- GOAL -->
<div class="statement-section statement-goal">
  <h1><span class="icon icon-goal">&nbsp;</span><span>Objectif</span></h1>
  <div class="statement-goal-content">
    Deux combattants s'affrontent sur un ring vu de côté. Chaque bot pilote l'un d'eux : un corps physique de 19 points mû par 15 moteurs d'articulation.
    Porter des coups, garder l'équilibre et rester dans le ring : le vainqueur amène l'adversaire à <const>0</const> PV, ou a plus de PV après <const>250</const> tours.<br><br>
    Code source du jeu (arbitre, moteur physique, visualiseur) : <a href="https://github.com/mehdiAyari/byte-club" target="_blank">github.com/mehdiAyari/byte-club</a>
  </div>
</div>

<!-- RULES -->
<div class="statement-section statement-rules">
  <h1><span class="icon icon-rules">&nbsp;</span><span>Règles</span></h1>
  <div>
    <div class="statement-rules-content">

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Le ring</h3>
<ul>
  <li>Vue de côté, en millimètres. L'origine est en haut à gauche de la scène : <var>x</var> augmente vers la droite, <var>y</var> augmente vers le bas, et le sol est en <var>y</var> = <const>3000</const>.</li>
  <li>Le ring fait 6 m de large : de <var>x</var> = <const>2000</const> à <var>x</var> = <const>8000</const>. Son centre est en <var>x</var> = <const>5000</const>.</li>
  <li><strong>Chaque joueur voit le match depuis la gauche</strong> : dans son entrée, son propre combattant est à gauche et regarde vers la droite. Le combattant de droite lit la scène en miroir (<var>x</var> devient 10000 &minus; <var>x</var>, <var>vx</var> devient &minus;<var>vx</var>). Les commandes ont le même sens des deux côtés.</li>
  <li>Les deux combattants commencent en STANCE, immobiles, à <var>startDistance</var> l'un de l'autre, bassin à bassin (de <const>1600</const> à <const>2400</const>, un nombre pair, tiré au hasard), de part et d'autre du centre. Un combattant regarde du même côté pendant tout le match.</li>
  <li>Chaque combattant commence avec <const>100</const> PV.</li>
</ul>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Le combattant</h3>
<p>Un combattant, c'est 19 points, 65,4 kg en tout, reliés par 20 os rigides. Les deux combattants se tiennent comme deux boxeurs droitiers face à face : LEAD est le côté de devant, REAR l'autre.</p>
<div style="text-align: center; margin: 12px 0"><img src="https://cdn-games.codingame.com/community/7212247-1794655631881/1bafa0923b79d39e3899b7ef88c461b9426426631168339d19f95097c718acb0.png" alt="Les 19 points d'un combattant, numérotés, côté avant en bleu, côté arrière en orange" style="max-width: 100%; border-radius: 8px" /></div>
<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 8px">#</th><th style="padding: 2px 8px">Point</th><th style="padding: 2px 8px">Masse (kg)</th><th style="padding: 2px 8px">Rayon (mm)</th><th style="padding: 2px 8px">Articulation qui le bouge</th></tr>
  <tr><td style="padding: 2px 8px">0</td><td style="padding: 2px 8px">HEAD</td><td style="padding: 2px 8px">5</td><td style="padding: 2px 8px">110</td><td style="padding: 2px 8px">0 NECK</td></tr>
  <tr><td style="padding: 2px 8px">1</td><td style="padding: 2px 8px">NECK (haut de la colonne, entre les épaules)</td><td style="padding: 2px 8px">6</td><td style="padding: 2px 8px">120</td><td style="padding: 2px 8px">1 UPPER_BACK</td></tr>
  <tr><td style="padding: 2px 8px">2</td><td style="padding: 2px 8px">CHEST</td><td style="padding: 2px 8px">9</td><td style="padding: 2px 8px">130</td><td style="padding: 2px 8px">2 LOWER_BACK</td></tr>
  <tr><td style="padding: 2px 8px">3</td><td style="padding: 2px 8px">WAIST</td><td style="padding: 2px 8px">9</td><td style="padding: 2px 8px">120</td><td style="padding: 2px 8px">aucune</td></tr>
  <tr><td style="padding: 2px 8px">4</td><td style="padding: 2px 8px">PELVIS (les hanches)</td><td style="padding: 2px 8px">12</td><td style="padding: 2px 8px">130</td><td style="padding: 2px 8px">aucune</td></tr>
  <tr><td style="padding: 2px 8px">5, 8</td><td style="padding: 2px 8px">LEAD_SHOULDER, REAR_SHOULDER</td><td style="padding: 2px 8px">2</td><td style="padding: 2px 8px">60</td><td style="padding: 2px 8px">3 LEAD_GIRDLE, 6 REAR_GIRDLE</td></tr>
  <tr><td style="padding: 2px 8px">6, 9</td><td style="padding: 2px 8px">LEAD_ELBOW, REAR_ELBOW</td><td style="padding: 2px 8px">1,5</td><td style="padding: 2px 8px">50</td><td style="padding: 2px 8px">4 LEAD_SHOULDER, 7 REAR_SHOULDER</td></tr>
  <tr><td style="padding: 2px 8px">7, 10</td><td style="padding: 2px 8px">LEAD_HAND, REAR_HAND</td><td style="padding: 2px 8px">1,2</td><td style="padding: 2px 8px">65</td><td style="padding: 2px 8px">5 LEAD_ELBOW, 8 REAR_ELBOW</td></tr>
  <tr><td style="padding: 2px 8px">11, 15</td><td style="padding: 2px 8px">LEAD_KNEE, REAR_KNEE</td><td style="padding: 2px 8px">4</td><td style="padding: 2px 8px">80</td><td style="padding: 2px 8px">9 LEAD_HIP, 12 REAR_HIP</td></tr>
  <tr><td style="padding: 2px 8px">12, 16</td><td style="padding: 2px 8px">LEAD_ANKLE, REAR_ANKLE</td><td style="padding: 2px 8px">2</td><td style="padding: 2px 8px">40</td><td style="padding: 2px 8px">10 LEAD_KNEE, 13 REAR_KNEE</td></tr>
  <tr><td style="padding: 2px 8px">13, 17</td><td style="padding: 2px 8px">LEAD_HEEL, REAR_HEEL</td><td style="padding: 2px 8px">0,5</td><td style="padding: 2px 8px">30</td><td style="padding: 2px 8px">aucune</td></tr>
  <tr><td style="padding: 2px 8px">14, 18</td><td style="padding: 2px 8px">LEAD_TOES, REAR_TOES</td><td style="padding: 2px 8px">1</td><td style="padding: 2px 8px">30</td><td style="padding: 2px 8px">11 LEAD_ANKLE, 14 REAR_ANKLE</td></tr>
</table>
<ul>
  <li>Os, en mm : NECK&ndash;HEAD <const>200</const>, CHEST&ndash;NECK <const>180</const>, WAIST&ndash;CHEST <const>180</const>, PELVIS&ndash;WAIST <const>140</const> ; pour chaque bras NECK&ndash;SHOULDER <const>130</const>, SHOULDER&ndash;ELBOW <const>300</const>, ELBOW&ndash;HAND <const>280</const> ; pour chaque jambe PELVIS&ndash;KNEE <const>450</const>, KNEE&ndash;ANKLE <const>450</const>, et le pied, un triangle rigide : le talon 50 mm derrière et 60 mm sous la cheville, les orteils 170 mm devant et 60 mm sous elle.</li>
  <li>Un point touche le sol quand il en est plus près que son rayon. Debout à plat, les talons et les orteils sont à 30 mm au-dessus du sol (<var>y</var> = <const>2970</const>).</li>
  <li>Face à l'autre combattant, un corps est fait de formes arrondies : la tête (rayon 110), le torse (capsules NECK&ndash;CHEST, CHEST&ndash;WAIST, WAIST&ndash;PELVIS de rayon 130, 120, 130), les bras et les avant-bras (50 et 45), les gants (cercles de rayon 65 autour des mains), les cuisses et les tibias (80 et 50), les pieds (capsules HEEL&ndash;TOES de rayon 30) et les genoux (cercles de rayon 75, qui ne font que frapper). Un combattant ne se heurte jamais lui-même.</li>
</ul>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Articulations et moteurs</h3>
<p>15 articulations, chacune actionnée par un moteur, dans cet ordre :</p>
<div style="text-align: center; margin: 12px 0"><img src="https://cdn-games.codingame.com/community/7212247-1794655631881/0745a659ab3c2e60401cfd91a2878d5286351d4d784803cd0230481ed724712c.png" alt="Où se trouve 0 degré et quel est le sens positif pour chaque articulation" style="max-width: 100%; border-radius: 8px" /></div>
<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 6px">#</th><th style="padding: 2px 6px">Articulation</th><th style="padding: 2px 6px">0&deg;</th><th style="padding: 2px 6px">Angles positifs</th><th style="padding: 2px 6px">Plage (&deg;)</th><th style="padding: 2px 6px">Couple max (N&middot;m)</th><th style="padding: 2px 6px">Vitesse max (rad/s)</th></tr>
  <tr><td style="padding: 2px 6px">0</td><td style="padding: 2px 6px">NECK</td><td style="padding: 2px 6px">tête dans l'axe du haut du dos</td><td style="padding: 2px 6px">tête en avant, menton rentré</td><td style="padding: 2px 6px">-30 à 45</td><td style="padding: 2px 6px">40</td><td style="padding: 2px 6px">10</td></tr>
  <tr><td style="padding: 2px 6px">1</td><td style="padding: 2px 6px">UPPER_BACK</td><td style="padding: 2px 6px">droit</td><td style="padding: 2px 6px">penché en avant</td><td style="padding: 2px 6px">-20 à 35</td><td style="padding: 2px 6px">120</td><td style="padding: 2px 6px">10</td></tr>
  <tr><td style="padding: 2px 6px">2</td><td style="padding: 2px 6px">LOWER_BACK</td><td style="padding: 2px 6px">droit</td><td style="padding: 2px 6px">penché en avant</td><td style="padding: 2px 6px">-25 à 40</td><td style="padding: 2px 6px">150</td><td style="padding: 2px 6px">10</td></tr>
  <tr><td style="padding: 2px 6px">3, 6</td><td style="padding: 2px 6px">LEAD_GIRDLE, REAR_GIRDLE</td><td style="padding: 2px 6px">épaule sur l'axe du torse, sous le cou</td><td style="padding: 2px 6px">épaule en avant</td><td style="padding: 2px 6px">-60 à 60</td><td style="padding: 2px 6px">120</td><td style="padding: 2px 6px">15</td></tr>
  <tr><td style="padding: 2px 6px">4, 7</td><td style="padding: 2px 6px">LEAD_SHOULDER, REAR_SHOULDER</td><td style="padding: 2px 6px">bras pendant le long du torse</td><td style="padding: 2px 6px">bras levé vers l'avant</td><td style="padding: 2px 6px">-60 à 180</td><td style="padding: 2px 6px">80</td><td style="padding: 2px 6px">40</td></tr>
  <tr><td style="padding: 2px 6px">5, 8</td><td style="padding: 2px 6px">LEAD_ELBOW, REAR_ELBOW</td><td style="padding: 2px 6px">bras tendu</td><td style="padding: 2px 6px">coude plié</td><td style="padding: 2px 6px">0 à 150</td><td style="padding: 2px 6px">60</td><td style="padding: 2px 6px">60</td></tr>
  <tr><td style="padding: 2px 6px">9, 12</td><td style="padding: 2px 6px">LEAD_HIP, REAR_HIP</td><td style="padding: 2px 6px">cuisse dans l'axe du torse</td><td style="padding: 2px 6px">cuisse levée vers l'avant</td><td style="padding: 2px 6px">-45 à 130</td><td style="padding: 2px 6px">220</td><td style="padding: 2px 6px">25</td></tr>
  <tr><td style="padding: 2px 6px">10, 13</td><td style="padding: 2px 6px">LEAD_KNEE, REAR_KNEE</td><td style="padding: 2px 6px">jambe tendue</td><td style="padding: 2px 6px">genou plié</td><td style="padding: 2px 6px">0 à 150</td><td style="padding: 2px 6px">220</td><td style="padding: 2px 6px">50</td></tr>
  <tr><td style="padding: 2px 6px">11, 14</td><td style="padding: 2px 6px">LEAD_ANKLE, REAR_ANKLE</td><td style="padding: 2px 6px">plante du pied à angle droit avec le tibia</td><td style="padding: 2px 6px">pointe du pied vers le bas</td><td style="padding: 2px 6px">-40 à 50</td><td style="padding: 2px 6px">150</td><td style="padding: 2px 6px">20</td></tr>
</table>
<ul>
  <li>Les ceintures scapulaires (GIRDLE), les épaules et les hanches se mesurent par rapport à l'axe du torse (du cou au bassin), toutes les autres articulations par rapport à l'os qui les précède, dans le repère propre de chaque combattant : la même commande fait le même mouvement des deux côtés.</li>
  <li>Chaque moteur tire son articulation vers son angle cible <var>target</var> avec un couple plafonné à <var>strength</var> (de <const>0</const> à <const>100</const> %) &times; couple max, tout le plafond à 10&deg; de la cible (5&deg; pour les chevilles). Comme un muscle, il s'affaiblit quand il va plus vite, jusqu'à 0 à la vitesse max. Une force de 0 laisse l'articulation relâchée ; une plage est une butée ; les cibles et les forces restent en place tant qu'on ne les change pas.</li>
</ul>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Un tour</h3>
<ol>
  <li>Les deux bots envoient une commande en même temps.</li>
  <li>Le jeu simule 0,1 s de physique en 40 pas de 2,5 ms. À chaque pas, les corps bougent et se poussent, et les coups qui touchent sont relevés.</li>
  <li>À la fin du tour, tout se règle : les PV pris par les coups, l'endurance, les chutes et les sorties du ring, et la fin du match.</li>
</ol>
<p>Un match dure au plus <const>250</const> tours : 25 secondes de combat.</p>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Commandes</h3>
<p>À chaque tour, un bot écrit une ligne avec l'une de ces commandes :</p>
<ul>
  <li><strong>Un mouvement</strong> du tableau ci-dessous, une étape par tour. Une pose tenue (STANCE, GUARD, RELAX, DUCK) dure tant qu'elle est envoyée. Un mouvement minuté dure le nombre de tours indiqué : renvoyer son nom, ou <action>WAIT</action>, le laisse continuer ; toute autre commande l'interrompt aussitôt. À la fin, le combattant revient en STANCE, et renvoyer son nom le relance.</li>
  <li><action>WAIT</action> : le mouvement en cours continue et la pose tenue est maintenue.
<!-- BEGIN level2 level3 -->
  Après <action>JOINTS</action>, les moteurs gardent leurs cibles et leurs forces.
<!-- END -->
  </li>
</ul>
<!-- BEGIN level2 -->
<div class="statement-new-league-rule">
<!-- END -->
<!-- BEGIN level2 level3 -->
<ul>
  <li><action>JOINTS t1 s1 t2 s2 ... t15 s15</action> règle directement les 15 moteurs, dans l'ordre des articulations : une cible en degrés et une force en % pour chaque articulation.
    <ul>
      <li>Chaque valeur est un nombre (un signe moins facultatif, jusqu'à 6 chiffres, éventuellement un point et jusqu'à 6 décimales), ou <action>*</action> pour garder la valeur actuelle. Une cible est arrondie à 0,01&deg;, une force à un nombre entier.</li>
      <li><action>ROUND</action> (en majuscules) après les 30 valeurs fait passer la jambe avant autour du corps de l'adversaire pour ce tour, comme HIGH_KICK (voir Coups).</li>
      <li><action>JOINTS</action> arrête le mouvement en cours. Les jambes ne suivent alors plus que leurs cibles : le moteur du jeu ne place plus les pieds et ne garde plus l'équilibre.</li>
      <li>Exemple : <action>JOINTS</action>, 14 <action>*</action>, <action>10 100</action>, 14 <action>*</action> baisse le bras arrière (articulation 7 à 10&deg; à 100 %) et garde tous les autres moteurs tels quels.</li>
    </ul>
  </li>
</ul>
<!-- END -->
<!-- BEGIN level2 -->
</div>
<!-- END -->
<p>Le texte écrit après la commande est un message, affiché au-dessus du combattant (40 caractères au plus).
<!-- BEGIN level2 level3 -->
Après <action>JOINTS</action>, le message ne doit pas commencer par un nombre ou par <action>*</action>.
<!-- END -->
</p>
<p>La ligne peut commencer par <action>LOOK skin hair</action> pour choisir l'apparence du combattant au premier tour (exemple : <action>LOOK 3 5 STEP_FORWARD</action> ; seul, c'est un <action>WAIT</action>). <var>skin</var> : de <const>0</const> (très claire) à <const>7</const> (très foncée). <var>hair</var> : <const>0</const> noirs, <const>1</const> brun foncé, <const>2</const> bruns, <const>3</const> châtain clair, <const>4</const> blond foncé, <const>5</const> blonds, <const>6</const> auburn, <const>7</const> roux, <const>8</const> gris, <const>9</const> chauve. Le short et les gants portent la couleur du joueur.</p>
<p><strong>Lignes fausses.</strong> Une ligne qui n'est pas une commande (vide, ou un nom inconnu : les noms sont en majuscules) fait perdre le match. Une commande reconnue mais qui ne peut pas être jouée telle quelle n'est pas jouée : la console dit pourquoi, et WAIT est joué à la place
<!-- BEGIN level1 -->
(par exemple <action>JOINTS</action>, qui n'est pas disponible dans cette ligue).
<!-- END -->
<!-- BEGIN level2 level3 -->
(par exemple <action>JOINTS</action> avec une valeur manquante, une de trop, ou une valeur qui n'est pas un nombre). Une valeur hors de sa plage est jouée comme la valeur valide la plus proche, et la console le dit.
<!-- END -->
Un <action>LOOK</action> inutilisable (un tour suivant, d'autres valeurs) est ignoré, avec un message, et le reste de la ligne est joué.</p>

<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 6px">Mouvement</th><th style="padding: 2px 6px">Tours</th><th style="padding: 2px 6px">Ce qu'il fait</th></tr>
  <tr><td style="padding: 2px 6px">STANCE</td><td style="padding: 2px 6px">tenue</td><td style="padding: 2px 6px">garde de boxe : pied avant devant, pied arrière 42 cm derrière lui, genoux un peu fléchis, gant avant à hauteur des yeux, gant arrière au menton</td></tr>
  <tr><td style="padding: 2px 6px">GUARD</td><td style="padding: 2px 6px">tenue</td><td style="padding: 2px 6px">garde haute : les deux gants devant le visage, les bras tenus plus fermement, le menton rentré</td></tr>
  <tr><td style="padding: 2px 6px">RELAX</td><td style="padding: 2px 6px">tenue</td><td style="padding: 2px 6px">bras pendants à 10 % de force : moins coûteux en endurance, mais la tête est découverte</td></tr>
  <tr><td style="padding: 2px 6px">DUCK</td><td style="padding: 2px 6px">tenue</td><td style="padding: 2px 6px">dos penché en avant, menton baissé, gants hauts, un quart du chemin plus bas à chaque tour : complètement baissé après 4 tours, la tête environ 35 cm plus bas</td></tr>
  <tr><td style="padding: 2px 6px">JAB</td><td style="padding: 2px 6px">3</td><td style="padding: 2px 6px">direct du bras avant en 1 tour, le bassin 11 cm plus en avant au-dessus des pieds ; puis 2 tours de retour en garde</td></tr>
  <tr><td style="padding: 2px 6px">CROSS</td><td style="padding: 2px 6px">3</td><td style="padding: 2px 6px">direct du bras arrière en 2 tours, le bassin 9 cm plus en avant ; puis 1 tour de retour</td></tr>
  <tr><td style="padding: 2px 6px">OVERHAND</td><td style="padding: 2px 6px">3</td><td style="padding: 2px 6px">le bras arrière arme en hauteur (1 tour), puis redescend par-dessus les gants de l'adversaire (1 tour) ; puis 1 tour de retour</td></tr>
  <tr><td style="padding: 2px 6px">KICK</td><td style="padding: 2px 6px">6</td><td style="padding: 2px 6px">coup de pied de la jambe avant : poids sur le pied arrière, genou levé, coup de pied, genou ramené, pied reposé, sur une jambe pendant ce temps</td></tr>
  <tr><td style="padding: 2px 6px">HIGH_KICK</td><td style="padding: 2px 6px">12</td><td style="padding: 2px 6px">coup de pied circulaire de la jambe avant à la tête : la jambe monte en passant autour du corps de l'adversaire, puis redescend</td></tr>
  <tr><td style="padding: 2px 6px">STEP_FORWARD</td><td style="padding: 2px 6px">7</td><td style="padding: 2px 6px">le pied avant avance d'environ 25 cm, puis le pied arrière le suit</td></tr>
  <tr><td style="padding: 2px 6px">STEP_BACK</td><td style="padding: 2px 6px">7</td><td style="padding: 2px 6px">le pied arrière part en premier, puis le pied avant le suit</td></tr>
  <tr><td style="padding: 2px 6px">JUMP</td><td style="padding: 2px 6px">7</td><td style="padding: 2px 6px">flexion, impulsion des deux jambes, genoux ramenés en l'air, réception : les pieds décollent du sol d'environ 30 cm</td></tr>
</table>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Jambes et équilibre</h3>
<p>Un combattant garde l'équilibre tant que son <strong>point de poids</strong> reste au-dessus de ses pieds : le <var>x</var> de son centre de gravité plus la vitesse horizontale du centre de gravité &times; &radic;(hauteur du centre de gravité au-dessus du sol &divide; 9,81), en m et en m/s.</p>
<div style="text-align: center; margin: 12px 0"><img src="https://cdn-games.codingame.com/community/7212247-1794655631881/23dc2f098bba196443e0fb3b604aa0c36546e859661c21dc8920db0e5d549d55.png" alt="Les zones de frappe, et le point de poids par rapport aux pieds" style="max-width: 100%; border-radius: 8px" /></div>
<ul>
  <li>Pour les poses tenues, les coups de poing et les pas, <strong>le moteur du jeu dirige les jambes</strong> à 100 % de force : il place le bassin au-dessus des pieds, les chevilles s'inclinent contre là où va le poids, et les pieds sont remis à 42 cm l'un de l'autre quand ils dérivent.</li>
  <li><strong>Pas d'équilibre</strong> : quand le point de poids dépasse de plus de 2 cm la pointe du pied avant ou le talon arrière, le pied de ce côté va se placer dessous. Le résumé de la partie signale ces pas. Pendant STEP_FORWARD et STEP_BACK, les pieds ne vont que dans le sens du pas : un pas en arrière alors que le poids part en avant (ou l'inverse) peut faire tomber le combattant.</li>
  <li>KICK, HIGH_KICK et JUMP laissent les jambes à leurs propres cibles : aucune aide pour l'équilibre.
<!-- BEGIN level2 level3 -->
  <action>JOINTS</action> aussi.
<!-- END -->
  </li>
  <li><var>rise</var> (de <const>0</const> à <const>4</const>) compte de combien de quarts le combattant est baissé : DUCK en ajoute un, les autres poses et les coups de poing en retirent un, et un coup de pied, un pas ou JUMP
<!-- BEGIN level2 level3 -->
  (ou <action>JOINTS</action>)
<!-- END -->
  le remet à <const>0</const>.</li>
</ul>
<p>Les valeurs exactes des jambes sont dans le code source (<var>Game.java</var>).</p>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Coups</h3>
<ul>
  <li>Les gants, les genoux et les pieds frappent. Les gants et les genoux marquent sur la tête et le torse ; les pieds marquent aussi sur les cuisses, les tibias et les pieds. Zone : tête <const>2</const>, torse <const>1</const>, jambes <const>0,5</const>.</li>
  <li>Un coup marque quand la partie qui frappe touche l'adversaire alors que les deux se rapprochent à <const>2</const> m/s ou plus, et que la partie qui frappe va elle-même vers la cible à <const>1</const> m/s ou plus. <strong>Dégâts = zone &times; (vitesse de rapprochement &minus; 2)</strong>, la vitesse de rapprochement en m/s étant plafonnée à <const>12</const>, arrondis au nombre entier le plus proche (les demis vers le haut). Exemple : un gant qui arrive sur la tête à 7 m/s prend 10 PV.</li>
  <li>Un coup qui arrive sur un bras ou un avant-bras est bloqué : aucun dégât. Toucher un gant ne fait rien non plus.</li>
  <li>Une touche par contact : une partie qui frappe ne marque de nouveau qu'après s'être éloignée de l'adversaire pendant 10 pas de physique (25 ms). Un pied ne marque que là où il touche à au moins 30 cm du sol. Plus rien ne marque une fois qu'un combattant est à terre.</li>
  <li><strong>Profondeur</strong> : le côté avant de chaque combattant fait face au côté arrière de l'autre. Un membre avant passe à côté des membres avant de l'adversaire et rencontre ses membres arrière, sa tête et son torse ; un membre arrière rencontre les membres avant de l'adversaire, sa tête et son torse. Ainsi, un JAB ne peut être bloqué que par le bras arrière, un CROSS par le bras avant. Les jambes passent les unes à côté des autres, sauf qu'un pied avant rencontre la cuisse arrière.</li>
  <li><strong>Coup de pied circulaire</strong> : pendant HIGH_KICK
<!-- BEGIN level2 level3 -->
  (ou un tour de <action>JOINTS ... ROUND</action>)
<!-- END -->
  la jambe avant passe autour du corps de l'adversaire : elle ne rencontre que la tête, et l'avant-bras et le gant arrière tant que ce gant est tenu haut.</li>
  <li>Chaque contact pousse aussi : les deux corps ne se chevauchent jamais, et un coup bloqué peut quand même déséquilibrer un combattant.</li>
</ul>
<p>Les coups contre un combattant qui tient une pose, à 0,8 ou 0,9 m (bassin à bassin), mesurés avec l'arbitre :</p>
<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 6px">Coup</th><th style="padding: 2px 6px">sur STANCE</th><th style="padding: 2px 6px">sur GUARD</th><th style="padding: 2px 6px">sur DUCK</th></tr>
  <tr><td style="padding: 2px 6px">JAB</td><td style="padding: 2px 6px">2 à 3 PV (tête)</td><td style="padding: 2px 6px">0</td><td style="padding: 2px 6px">0</td></tr>
  <tr><td style="padding: 2px 6px">CROSS</td><td style="padding: 2px 6px">4 à 7 PV (tête)</td><td style="padding: 2px 6px">1 à 2 PV</td><td style="padding: 2px 6px">0</td></tr>
  <tr><td style="padding: 2px 6px">OVERHAND</td><td style="padding: 2px 6px">8 à 10 PV (tête)</td><td style="padding: 2px 6px">6 à 7 PV</td><td style="padding: 2px 6px">0</td></tr>
  <tr><td style="padding: 2px 6px">KICK</td><td style="padding: 2px 6px">5 à 6 PV (cuisse, deux touches)</td><td style="padding: 2px 6px">4 à 6 PV</td><td style="padding: 2px 6px">4 à 5 PV</td></tr>
  <tr><td style="padding: 2px 6px">HIGH_KICK</td><td style="padding: 2px 6px">5 à 7 PV (tête) à partir de 0,85 m ; moins de près</td><td style="padding: 2px 6px">11 à 12 PV (tête) : il contourne la garde</td><td style="padding: 2px 6px">0</td></tr>
</table>

<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Chutes et sorties du ring</h3>
<ul>
  <li>Seuls les pieds (chevilles, talons et orteils) peuvent toucher le sol. Quand un autre point le touche, le combattant est <strong>à terre</strong>. Quand un point touche le sol hors du ring (<var>x</var> en dessous de <const>2000</const> ou au-dessus de <const>8000</const>), le combattant est <strong>sorti</strong>.</li>
  <li>Être à terre ou sorti coûte <const>20</const> PV, sauf après une poussée : quand les combattants se sont touchés pendant ce tour ou les 20 tours d'avant, et qu'aucun coup n'a pris de PV pendant ce temps, la chute est gratuite.</li>
  <li>Après un tour avec une chute ou une sortie, les deux combattants retournent à leur place de départ, en STANCE et immobiles. Les PV et l'endurance restent tels quels.</li>
</ul>

<!-- BEGIN level1 level2 -->
<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Endurance</h3>
<p>L'endurance ne sert pas dans cette ligue : elle vaut toujours <const>100000</const> (100 %).</p>
<!-- END -->
<!-- BEGIN level3 -->
<div class="statement-new-league-rule">
<h3 style="font-size: 16px; font-weight: 700; padding-top: 20px; color: #838891; padding-bottom: 15px">Endurance</h3>
<ul>
  <li>L'endurance se compte en millièmes de pour cent : <const>100000</const>, c'est 100 %, au départ comme au maximum.</li>
  <li>À chaque tour, avant sa physique, un combattant récupère 3,5 % et paie la tension de ses moteurs : chaque articulation coûte sa force (en %) &times; 0,002 % pour le cou et le dos, 0,003 % pour les ceintures scapulaires, 0,004 % pour les autres articulations. Un mouvement coûte les forces de sa propre étape.</li>
  <li>Un coup qui prend <var>n</var> PV prend aussi <var>n</var> % d'endurance, à la fin du tour.</li>
  <li>Un combattant fatigué est plus faible : le plafond de couple de chaque moteur est multiplié par 0,4 + 0,6 &times; endurance &divide; 100 %.</li>
</ul>
<p>Endurance gagnée (+) ou dépensée (&minus;) sur un mouvement entier, récupération comprise, sans coup reçu et quand le mouvement commence à 98 % ou moins :</p>
<table style="border-collapse: collapse; margin: 8px 0">
  <tr><th style="padding: 2px 6px">Mouvement</th><th style="padding: 2px 6px">Endurance</th><th style="padding: 2px 6px">Mouvement</th><th style="padding: 2px 6px">Endurance</th></tr>
  <tr><td style="padding: 2px 6px">STANCE</td><td style="padding: 2px 6px">+0,24 % par tour</td><td style="padding: 2px 6px">OVERHAND (3 tours)</td><td style="padding: 2px 6px">&minus;2,12 %</td></tr>
  <tr><td style="padding: 2px 6px">GUARD</td><td style="padding: 2px 6px">&minus;0,40 % par tour</td><td style="padding: 2px 6px">KICK (6 tours)</td><td style="padding: 2px 6px">&minus;1,30 %</td></tr>
  <tr><td style="padding: 2px 6px">RELAX</td><td style="padding: 2px 6px">+0,72 % par tour</td><td style="padding: 2px 6px">HIGH_KICK (12 tours)</td><td style="padding: 2px 6px">&minus;1,00 %</td></tr>
  <tr><td style="padding: 2px 6px">DUCK</td><td style="padding: 2px 6px">&minus;0,40 % par tour</td><td style="padding: 2px 6px">STEP_FORWARD (7 tours)</td><td style="padding: 2px 6px">&minus;0,84 %</td></tr>
  <tr><td style="padding: 2px 6px">JAB (3 tours)</td><td style="padding: 2px 6px">&minus;0,70 %</td><td style="padding: 2px 6px">STEP_BACK (7 tours)</td><td style="padding: 2px 6px">&minus;0,50 %</td></tr>
  <tr><td style="padding: 2px 6px">CROSS (3 tours)</td><td style="padding: 2px 6px">&minus;2,12 %</td><td style="padding: 2px 6px">JUMP (7 tours)</td><td style="padding: 2px 6px">+1,13 %</td></tr>
</table>
<p>L'endurance ne dépasse jamais 100 % : ce qu'un tour ajouterait au-delà est perdu. Un JUMP commencé à 100 % finit à 100 %, et un HIGH_KICK commencé à 100 % coûte 1,60 %.</p>
</div>
<!-- END -->

    </div>
  </div>
</div>

<!-- Victory conditions -->
<div class="statement-victory-conditions">
  <div class="icon victory"></div>
  <div class="blk">
    <div class="title">Conditions de victoire</div>
    <div class="text">
      <ul style="padding-top: 0; padding-bottom: 0;">
        <li>Amener l'adversaire à <const>0</const> PV ou moins (un KO).</li>
        <li>Avoir plus de PV que l'adversaire après <const>250</const> tours.</li>
        <li>Avoir plus de PV que l'adversaire quand les combattants ne se sont pas touchés pendant <const>150</const> tours : le match s'arrête là.</li>
      </ul>
      Les mêmes PV à la fin, ou les deux combattants à 0 PV au même tour, c'est un match nul.
    </div>
  </div>
</div>
<!-- Lose conditions -->
<div class="statement-lose-conditions">
  <div class="icon lose"></div>
  <div class="blk">
    <div class="title">Conditions de défaite</div>
    <div class="text">
      <ul style="padding-top: 0; padding-bottom: 0;">
        <li>Tomber à <const>0</const> PV, ou avoir moins de PV que l'adversaire à la fin du match.</li>
        <li>Écrire une ligne qui n'est pas une commande, ou ne pas répondre à temps : le match est perdu aussitôt (nul si les deux joueurs le font au même tour).</li>
      </ul>
    </div>
  </div>
</div>

<!-- EXPERT RULES -->
<div class="statement-section statement-expertrules">
  <h1><span class="icon icon-expertrules">&nbsp;</span><span>Règles pour les experts</span></h1>
  <div class="statement-expert-rules-content">
    <ul>
      <li><strong>L'entrée contient tout l'état du match.</strong> Le moteur est sur <a href="https://github.com/mehdiAyari/byte-club" target="_blank">GitHub</a> (classes <var>Game</var>, <var>Physics</var>, <var>Moves</var> et <var>Command</var>). Une copie reconstruite avec <var>Game.fromInput</var> contient le combattant du joueur en combattant 0, à gauche, comme l'entrée le montre : avec les deux commandes (<var>apply(0, ...)</var> pour le joueur, <var>apply(1, ...)</var> pour l'adversaire), elle calcule exactement l'entrée suivante, <var>inputLines(0)</var>. Seule la commande de l'adversaire est inconnue.</li>
      <li>Rien n'est aléatoire pendant un match. Seule la distance de départ est tirée ; dans l'IDE, le paramètre de jeu <var>distance</var> la fixe.</li>
      <li>Un pas de physique n'utilise que des additions, soustractions, multiplications, divisions et racines carrées sur des nombres à virgule flottante de 64 bits : il donne les mêmes nombres dans tous les langages.</li>
      <li>Un pas de 2,5 ms : la gravité et les moteurs changent les vitesses, les points bougent, les coups sont relevés (leurs PV sont pris à la fin du tour), puis les os, les plages des articulations, les contacts et le sol sont imposés. Les contacts entre les combattants sont résolus dans les deux ordres puis moyennés : un match et son image dans un miroir se jouent de la même façon. L'ordre exact et la loi des moteurs sont dans <var>Physics.java</var>.</li>
      <li>À la fin d'un tour, les positions sont arrondies au millimètre et les vitesses au millimètre par seconde (les demis vers le nombre pair) : le tour suivant part exactement de ce que dit l'entrée. Les angles de l'entrée sont calculés à partir des points puis arrondis, les cibles sont exactes ; la vitesse d'une articulation se calcule à partir des vitesses des points (<var>Physics.angSpeed</var>).</li>
    </ul>
  </div>
</div>

<!-- PROTOCOL -->
<div class="statement-section statement-protocol">
  <h1><span class="icon icon-protocol">&nbsp;</span><span>Entrées du jeu</span></h1>
  <div class="blk">
    <div class="title">Entrée d'initialisation</div>
    <div class="text">
      <span class="statement-lineno">Première ligne :</span> un entier <var>startDistance</var> : la distance entre les deux bassins au départ, en mm.
    </div>
  </div>
  <div class="blk">
    <div class="title">Entrée pour un tour de jeu</div>
    <div class="text">
      4 lignes. Le combattant du joueur vient toujours en premier, et se voit à gauche (voir Le ring) : <var>x</var> augmente vers la droite et <var>y</var> vers le bas, en mm et en mm/s.<br>
      <span class="statement-lineno">2 premières lignes :</span> 6 valeurs séparées par des espaces <var>hp</var> <var>stamina</var> <var>moveName</var> <var>frame</var> <var>rise</var> <var>roundKick</var> (<var>moveName</var> est une chaîne, les autres sont des entiers), pour le combattant du joueur, puis pour l'adversaire : les PV ; l'endurance en millièmes de pour cent (de <const>0</const> à <const>100000</const>) ; le mouvement en cours du combattant, et combien de ses tours ont été joués (<const>0</const> pour une pose tenue) ;
<!-- BEGIN level2 level3 -->
      après une commande <action>JOINTS</action>, le mouvement est <action>JOINTS</action> et l'étape <const>0</const> ;
<!-- END -->
      de combien le combattant est baissé (de <const>0</const> à <const>4</const> quarts) ; <const>1</const> quand sa jambe avant est passée autour de l'autre corps au tour précédent (coup de pied circulaire), sinon <const>0</const>.<br>
      <span class="statement-lineno">Ligne suivante :</span> 304 valeurs séparées par des espaces : 8 valeurs <var>x</var> <var>y</var> <var>vx</var> <var>vy</var> <var>apart</var> <var>angle</var> <var>target</var> <var>strength</var> pour chacun des 38 points (<var>angle</var> et <var>target</var> sont des nombres décimaux, les autres des entiers) : les 19 points du joueur, puis ceux de l'adversaire, dans l'ordre du tableau des points.
      <ul>
        <li><var>x</var> <var>y</var> <var>vx</var> <var>vy</var> : position et vitesse du point. Par exemple, la distance entre les deux bassins est le <var>x</var> du PELVIS de l'adversaire (point 23 de la ligne) moins le <var>x</var> de celui du joueur (point 4).</li>
        <li><var>apart</var> : le nombre de pas de physique depuis que la partie qui frappe à ce point a touché l'autre combattant pour la dernière fois (de <const>0</const> à <const>10</const> ; elle peut marquer de nouveau à <const>10</const>). Les gants sont aux mains, les pieds aux orteils, les genoux aux genoux ; <const>10</const> pour les points qui ne frappent pas.</li>
        <li><var>angle</var> <var>target</var> <var>strength</var> : le moteur de l'articulation qui bouge ce point (voir le tableau des points), tel qu'il est maintenant, jambes dirigées par le moteur du jeu comprises : angle et cible en degrés (2 décimales), force en %. <const>0.00 0.00 0</const> pour WAIST, PELVIS et les talons, qu'aucune articulation ne bouge. Lus dans l'ordre sans ces quatre points, les points donnent les articulations dans l'ordre des articulations.</li>
      </ul>
      <span class="statement-lineno">Dernière ligne :</span> 2 entiers séparés par un espace <var>sinceContact</var> <var>sinceHit</var> : le nombre de tours depuis que les combattants se sont touchés, et depuis qu'un coup a pris des PV (<const>0</const> : pendant le dernier tour). Les deux valent <const>21</const> au départ et après une chute ; <var>sinceContact</var> compte jusqu'à <const>150</const>, <var>sinceHit</var> jusqu'à <const>21</const>.
    </div>
  </div>
  <div class="blk">
    <div class="title">Sortie pour un tour de jeu</div>
    <div class="text">
      <span class="statement-lineno">Une ligne :</span> une commande (voir Commandes), suivie ou non d'un message.
    </div>
  </div>
  <div class="blk">
    <div class="title">Contraintes</div>
    <div class="text">
      <const>1600</const> &le; <var>startDistance</var> &le; <const>2400</const><br>
      Temps de réponse au premier tour &le; <const>1000</const> ms<br>
      Temps de réponse par tour &le; <const>50</const> ms<br>
      Au plus <const>250</const> tours
    </div>
  </div>
</div>
<!-- BEGIN level1 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-top: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">Qu'est-ce qui m'attend dans les ligues suivantes ?</p>
  Les règles supplémentaires disponibles dans les ligues supérieures seront les suivantes :
  <ul>
    <li>La commande <action>JOINTS</action> : un bot pilote lui-même les 15 articulations, avec un angle visé et une force pour chacune.</li>
    <li><strong>L'endurance</strong> : chaque muscle contracté coûte de l'endurance, chaque coup reçu en retire, et un combattant fatigué est plus faible.</li>
  </ul>
</div>
<!-- END -->
<!-- BEGIN level2 -->
<div style="color: #7cc576; background-color: rgba(124, 197, 118,.1); padding: 20px; margin-top: 10px; text-align: left;">
  <div style="text-align: center; margin-bottom: 6px"><img src="//cdn.codingame.com/smash-the-code/statement/league_wood_04.png" /></div>
  <p style="text-align: center; font-weight: 700; margin-bottom: 6px;">Qu'est-ce qui m'attend dans les ligues suivantes ?</p>
  La ligue suivante ajoute une règle :
  <ul>
    <li><strong>L'endurance</strong> : chaque muscle contracté coûte de l'endurance, chaque coup reçu en retire, et un combattant fatigué est plus faible.</li>
  </ul>
</div>
<!-- END -->
</div>
