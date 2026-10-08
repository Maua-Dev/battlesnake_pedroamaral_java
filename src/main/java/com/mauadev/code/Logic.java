package com.mauadev.code;

import com.mauadev.code.entities.Board;
import com.mauadev.code.entities.Coordinate;
import com.mauadev.code.entities.GameState;
import com.mauadev.code.entities.Snake;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cobra do campeonato: escolhe a jogada olhando só o turno atual.
 * Evita paredes e corpos, foge de cabeças maiores, procura espaço livre e busca comida quando a vida baixa.
 */
public class Logic {

    private static final String[] MOVES = {"up", "down", "left", "right"};
    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {1, -1, 0, 0};

    /** Abaixo dessa vida a cobra passa a ir atrás da comida mais próxima. */
    private static final int HUNGRY = 40;

    /** GET / - aparência da cobra. */
    public static Map<String, String> info() {
        Map<String, String> info = new HashMap<>();
        info.put("apiversion", "1");
        info.put("author", "PedroAAmaral");          // TODO: coloque aqui o SEU usuário do Battlesnake
        info.put("color", "#024413d5");    // TODO: escolha a cor da sua cobra
        info.put("head", "tongue");  // TODO: escolha a cabeça
        info.put("tail", "small-rattle");        // TODO: escolha a cauda
        info.put("version", "6.0.0-java");
        return info;
    }

    /** POST /start - uma vez por partida. */
    public static void start(GameState state) {
    }

    /** POST /end - uma vez por partida. */
    public static void end(GameState state) {
    }

    /** POST /move - a cada turno. Nunca lança exceção. */
    public static String getMove(GameState state) {
        try {
            return choose(state);
        } catch (RuntimeException e) {
            return "up";
        }
    }

    private static String choose(GameState state) {
        Board board = state.getBoard();
        int w = board.getWidth();
        int h = board.getHeight();
        Snake me = state.getYou();
        List<Coordinate> myBody = me.getBody();
        Coordinate head = myBody.get(0);
        int myLength = myBody.size();

        boolean[] blocked = new boolean[w * h];
        boolean[] danger = new boolean[w * h];
        boolean[] food = new boolean[w * h];

        for (Coordinate f : board.getFood()) {
            food[f.getY() * w + f.getX()] = true;
        }

        for (Snake s : board.getSnakes()) {
            List<Coordinate> body = s.getBody();
            int n = body.size();
            // a cauda libera a casa no turno seguinte, a não ser que a cobra tenha acabado de comer
            boolean tailMoves = n > 1 && !sameCell(body.get(n - 1), body.get(n - 2));
            for (int i = 0; i < n; i++) {
                if (i == n - 1 && tailMoves) {
                    continue;
                }
                Coordinate c = body.get(i);
                blocked[c.getY() * w + c.getX()] = true;
            }
            if (!s.getId().equals(me.getId()) && n >= myLength) {
                Coordinate eh = body.get(0);
                for (int d = 0; d < 4; d++) {
                    int x = eh.getX() + DX[d];
                    int y = eh.getY() + DY[d];
                    if (inside(x, y, w, h)) {
                        danger[y * w + x] = true;
                    }
                }
            }
        }

        double cx = (w - 1) / 2.0;
        double cy = (h - 1) / 2.0;
        int bestMove = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int d = 0; d < 4; d++) {
            int x = head.getX() + DX[d];
            int y = head.getY() + DY[d];
            if (!inside(x, y, w, h) || blocked[y * w + x]) {
                continue;
            }
            int cell = y * w + x;
            int area = reachable(cell, blocked, w, h, 3 * myLength);
            double score = 10.0 * Math.min(area, 2 * myLength + 6);
            if (area < myLength) {
                score -= 400.0;
            }
            if (danger[cell]) {
                score -= 250.0;
            }
            if (me.getHealth() < HUNGRY) {
                int dist = foodDistance(cell, blocked, food, w, h);
                if (dist >= 0) {
                    score -= 9.0 * dist;
                }
            }
            score -= Math.abs(x - cx) + Math.abs(y - cy);
            if (score > bestScore) {
                bestScore = score;
                bestMove = d;
            }
        }

        return bestMove < 0 ? "up" : MOVES[bestMove];
    }

    private static boolean sameCell(Coordinate a, Coordinate b) {
        return a.getX() == b.getX() && a.getY() == b.getY();
    }

    private static boolean inside(int x, int y, int w, int h) {
        return x >= 0 && x < w && y >= 0 && y < h;
    }

    /** Quantas casas livres dá para alcançar a partir de 'start' (para ao chegar em 'limit'). */
    private static int reachable(int start, boolean[] blocked, int w, int h, int limit) {
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        int count = 0;
        while (!queue.isEmpty() && count < limit) {
            int c = queue.poll();
            count++;
            int x = c % w;
            int y = c / w;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (!inside(nx, ny, w, h)) {
                    continue;
                }
                int n = ny * w + nx;
                if (!seen[n] && !blocked[n]) {
                    seen[n] = true;
                    queue.add(n);
                }
            }
        }
        return count;
    }

    /** Menor número de passos de 'start' até uma comida, ou -1 se não houver caminho. */
    private static int foodDistance(int start, boolean[] blocked, boolean[] food, int w, int h) {
        if (food[start]) {
            return 0;
        }
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{start, 0});
        seen[start] = true;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int x = cur[0] % w;
            int y = cur[0] / w;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (!inside(nx, ny, w, h)) {
                    continue;
                }
                int n = ny * w + nx;
                if (seen[n] || blocked[n]) {
                    continue;
                }
                if (food[n]) {
                    return cur[1] + 1;
                }
                seen[n] = true;
                queue.add(new int[]{n, cur[1] + 1});
            }
        }
        return -1;
    }
}