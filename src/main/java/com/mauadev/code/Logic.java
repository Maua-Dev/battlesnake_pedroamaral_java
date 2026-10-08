package com.mauadev.code;

import com.mauadev.code.entities.Board;
import com.mauadev.code.entities.Coordinate;
import com.mauadev.code.entities.GameState;
import com.mauadev.code.entities.Snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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

    /** Tamanho padrão do tabuleiro quando o JSON não informa largura/altura. */
    private static final int DEFAULT_SIZE = 11;

    /** GET / - aparência da cobra. */
    public static Map<String, String> info() {
        Map<String, String> info = new HashMap<>();
        info.put("apiversion", "1");
        info.put("author", "PedroAAmaral");          // TODO: coloque aqui o SEU usuário do Battlesnake
        info.put("color", "#440202d5");    // TODO: escolha a cor da sua cobra
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
            return fallback(state);
        }
    }

    private static String choose(GameState state) {
        Board board = state.getBoard();
        int w = board.getWidth() > 0 ? board.getWidth() : DEFAULT_SIZE;
        int h = board.getHeight() > 0 ? board.getHeight() : DEFAULT_SIZE;
        Snake me = state.getYou();
        List<Coordinate> myBody = bodyOf(me);
        Coordinate head = myBody.get(0);
        int myLength = myBody.size();

        boolean[] blocked = new boolean[w * h];
        boolean[] danger = new boolean[w * h];
        boolean[] food = new boolean[w * h];

        if (board.getFood() != null) {
            for (Coordinate f : board.getFood()) {
                if (f != null && inside(f.getX(), f.getY(), w, h)) {
                    food[f.getY() * w + f.getX()] = true;
                }
            }
        }

        for (Snake s : allSnakes(state)) {
            List<Coordinate> body = bodyOf(s);
            int n = body.size();
            if (n == 0) {
                continue;
            }
            // a cauda libera a casa no turno seguinte, a não ser que a cobra tenha acabado de comer
            boolean tailMoves = n > 1 && !sameCell(body.get(n - 1), body.get(n - 2));
            for (int i = 0; i < n; i++) {
                if (i == n - 1 && tailMoves) {
                    continue;
                }
                Coordinate c = body.get(i);
                if (inside(c.getX(), c.getY(), w, h)) {
                    blocked[c.getY() * w + c.getX()] = true;
                }
            }
            if (!isMe(s, me) && n >= myLength) {
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

        return bestMove < 0 ? fallback(state) : MOVES[bestMove];
    }

    /** Jogada de emergência: só olha paredes e o próprio corpo. */
    private static String fallback(GameState state) {
        try {
            Board board = state.getBoard();
            int w = board != null && board.getWidth() > 0 ? board.getWidth() : DEFAULT_SIZE;
            int h = board != null && board.getHeight() > 0 ? board.getHeight() : DEFAULT_SIZE;
            List<Coordinate> body = bodyOf(state.getYou());
            Coordinate head = body.get(0);
            for (int d = 0; d < 4; d++) {
                int x = head.getX() + DX[d];
                int y = head.getY() + DY[d];
                if (!inside(x, y, w, h)) {
                    continue;
                }
                boolean onBody = false;
                for (int i = 0; i < body.size() - 1; i++) {
                    if (body.get(i).getX() == x && body.get(i).getY() == y) {
                        onBody = true;
                    }
                }
                if (!onBody) {
                    return MOVES[d];
                }
            }
        } catch (RuntimeException e) {
            // sem dados suficientes: cai no valor padrão
        }
        return "up";
    }

    /** Corpo da cobra; se vier vazio usa só a cabeça. Nunca devolve null nem coordenadas nulas. */
    private static List<Coordinate> bodyOf(Snake s) {
        if (s == null) {
            return Collections.emptyList();
        }
        List<Coordinate> out = new ArrayList<>();
        if (s.getBody() != null) {
            for (Coordinate c : s.getBody()) {
                if (c != null) {
                    out.add(c);
                }
            }
        }
        if (out.isEmpty() && s.getHead() != null) {
            out.add(s.getHead());
        }
        return out;
    }

    /** Todas as cobras do tabuleiro, incluindo a minha mesmo que o JSON não a liste. */
    private static List<Snake> allSnakes(GameState state) {
        List<Snake> all = new ArrayList<>();
        Snake me = state.getYou();
        boolean listed = false;
        if (state.getBoard() != null && state.getBoard().getSnakes() != null) {
            for (Snake s : state.getBoard().getSnakes()) {
                if (s == null) {
                    continue;
                }
                all.add(s);
                if (isMe(s, me)) {
                    listed = true;
                }
            }
        }
        if (!listed && me != null) {
            all.add(me);
        }
        return all;
    }

    /** Mesma cobra por referência, por id ou (sem id) por cabeça e tamanho idênticos. */
    private static boolean isMe(Snake s, Snake me) {
        if (s == me) {
            return true;
        }
        if (s.getId() != null && me.getId() != null) {
            return s.getId().equals(me.getId());
        }
        List<Coordinate> a = bodyOf(s);
        List<Coordinate> b = bodyOf(me);
        return !a.isEmpty() && a.size() == b.size() && sameCell(a.get(0), b.get(0));
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