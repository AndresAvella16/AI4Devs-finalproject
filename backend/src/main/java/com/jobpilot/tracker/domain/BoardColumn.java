package com.jobpilot.tracker.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Orden de las tarjetas dentro de una columna del tablero (Java puro). {@code boardPosition} es el índice de la
 * tarjeta en su columna: el servidor coloca la tarjeta movida en el índice pedido y renumera la columna de 0 a n−1,
 * así el orden no depende de desempates por {@code created_at}.
 */
public final class BoardColumn {

    private BoardColumn() {
    }

    /**
     * Coloca {@code card} en {@code index} entre las tarjetas de {@code column} (en su orden actual; si ya contiene
     * la tarjeta, se ignora esa aparición) y renumera. Un índice mayor que el tamaño la deja al final.
     *
     * @return la columna resultante, en orden
     */
    public static List<Application> place(List<Application> column, Application card, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("El índice no puede ser negativo: " + index);
        }
        List<Application> ordered = new ArrayList<>(column.size() + 1);
        for (Application other : column) {
            if (!other.getId().equals(card.getId())) {
                ordered.add(other);
            }
        }
        ordered.add(Math.min(index, ordered.size()), card);
        compact(ordered);
        return ordered;
    }

    /** Renumera de 0 a n−1 respetando el orden de la lista (cierra los huecos que deja una tarjeta que se va). */
    public static void compact(List<Application> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(i);
        }
    }
}
