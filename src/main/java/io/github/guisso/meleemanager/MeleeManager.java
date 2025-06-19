/*
 * The MIT License
 *
 * Copyright 2025 Luis Guisso &lt;luis.guisso at ifnmg.edu.br&gt;.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.github.guisso.meleemanager;

import io.github.guisso.meleeinterface.Decision;
import io.github.guisso.meleeinterface.IPlayer;
import io.github.guisso.meleeinterface.Payoff;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages disputes between players.
 * 
 * @author Luis Guisso &lt;luis.guisso at ifnmg.edu.br&gt;
 * @version 0.2
 * @since 0.1, 2025-03-11
 */
public final class MeleeManager {

    private static final int ROUNDS = 200;
    
    // Players and their scores
    private static Map<IPlayer, Integer> totalScore;

    public static void main(String[] args) {
        try {
            // Load all player classes into the "players" directory (*.jar)
            List<IPlayer> players = loadPlayers();

            totalScore = players.stream()
                    .collect(Collectors.toMap(k -> k, v -> 0));

            System.out.println("--- Loaded players ---");

            for (IPlayer player : players) {
                System.out.println(player.getDeveloperName()
                        + " -> " + player.getEngineName()
                );
            }

            // Contest: compete with each other
            System.out.println("\n--- Contest ---");

            int n = players.size();
            for (int i = 0; i < n; i++) {
                // You x You?? No: j = i + 1, Yes: j = i
                for (int j = i + 1; j < n; j++) {
                    melee(players.get(i), players.get(j));
                }
            }

            // Final result
            System.out.println("\n--- Final Result ---");

            for (var entry : totalScore.entrySet()) {
                System.out.println(" > %5d".formatted(entry.getValue())
                        + " "
                        + entry.getKey().getEngineName());
            }

        } catch (ClassNotFoundException | IOException | NoSuchMethodException
                | InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(MeleeManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static List<IPlayer> loadPlayers()
            throws ClassNotFoundException, MalformedURLException, IOException,
            NoSuchMethodException, InstantiationException,
            IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {

        File playersDir = new File("players");

        if (!playersDir.exists() || !playersDir.isDirectory()) {
            throw new RuntimeException("Players' directory not found");
        }

        List<IPlayer> players = new ArrayList<>();

        for (File file : playersDir.listFiles()) {
            if (file.getName().endsWith(".jar")) {

                URL jarUrl = file.toURI().toURL();

                try (URLClassLoader classLoader
                        = new URLClassLoader(new URL[]{jarUrl}); //
                         JarFile jarFile = new JarFile(file)) {

                    Enumeration<JarEntry> entries = jarFile.entries();

                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();

                        if (name.endsWith(".class")) {
                            String className = name.replace('/', '.').replace(".class", "");

                            Class<?> clazz = classLoader.loadClass(className);

                            if (IPlayer.class.isAssignableFrom(clazz)) {
                                IPlayer player = (IPlayer) clazz
                                        .getDeclaredConstructor().newInstance();
                                players.add(player);
                            }
                        }
                    }
                }
            }
        }

        return players;
    }

    private static int[] melee(IPlayer p1, IPlayer p2) {

        int[] score = new int[2];

        try {
            // First random player
            if (SecureRandom.getInstanceStrong().nextBoolean()) {
                IPlayer tmp = p1;
                p1 = p2;
                p2 = tmp;
            }
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(MeleeManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        int align = 60;

        StringBuilder output = new StringBuilder();
        output.append("-".repeat(align)).append("\n");

        // Players
        output.append(("%" + ((align + p1.getEngineName().length()) / 2) + "s\n")
                .formatted(p1.getEngineName()));
        output.append(("%" + ((align + "versus".length()) / 2) + "s\n")
                .formatted("versus"));
        output.append(("%" + ((align + p2.getEngineName().length()) / 2) + "s\n")
                .formatted(p2.getEngineName()));

        output.append("-".repeat(align)).append("\n");

        // Center with special markers (eg.: ... [?])
        align = 24;

        // #2 initial decision
        Decision p1Decision, p2Decision = Decision.NONE;

        for (int i = 0; i < ROUNDS; i++) {

            p1Decision = p1.makeMyMove(p2Decision);
            p2Decision = p2.makeMyMove(p1Decision);

            if (p1Decision == p2Decision) {
                // C/C or D/D

                if (p1Decision == Decision.COOPERATE) {
                    // Booth cooperates
                    score[0] += Payoff.REWARD.value;
                    score[1] += Payoff.REWARD.value;

                    output.append(("%" + align + "s [%d] x [%d] %s\n").formatted(
                            p1Decision, Payoff.REWARD.value,
                            Payoff.REWARD.value, p2Decision));
                } else {
                    // Booth defect
                    score[0] += Payoff.PUNISHMENT.value;
                    score[1] += Payoff.PUNISHMENT.value;

                    output.append(("%" + align + "s [%d] x [%d] %s\n").formatted(
                            p1Decision, Payoff.PUNISHMENT.value,
                            Payoff.PUNISHMENT.value, p2Decision));
                }

            } else {
                // D/C or C/D

                if (p1Decision == Decision.DEFECT) {
                    // #1 defect and #2 cooperates
                    score[0] += Payoff.TEMPTATION.value;
//                    score[1] += Payoff.SUCKER.value;

                    output.append(("%" + align + "s [%d] x [%d] %s\n").formatted(
                            p1Decision, Payoff.TEMPTATION.value,
                            Payoff.SUCKER.value, p2Decision));
                } else {
                    // #2 defect and #1 cooperate
//                    score[0] += Payoff.SUCKER.value;
                    score[1] += Payoff.TEMPTATION.value;

                    output.append(("%" + align + "s [%d] x [%d] %s\n").formatted(
                            p1Decision, Payoff.SUCKER.value,
                            Payoff.TEMPTATION.value, p2Decision));
                }
            }
        }

        totalScore.merge(p1, score[0], Integer::sum);
        totalScore.merge(p2, score[1], Integer::sum);

        output.append(("%" + (align + 3) + "d --- %d").formatted(score[0], score[1]));

        System.out.println(output);
        return score;
    }
}
