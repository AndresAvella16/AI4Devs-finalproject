package com.jobpilot.tracker.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** El servidor renumera de 0 a n−1 la columna en la que se coloca una tarjeta y la columna que pierde una. */
class BoardColumnTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    private static Application card(int position) {
        return new Application(UUID.randomUUID(), UUID.randomUUID(), ApplicationStatus.GUARDADA, position, null, null,
                null, NOW);
    }

    private static List<Application> column(int size) {
        List<Application> cards = new ArrayList<>();
        IntStream.range(0, size).forEach(i -> cards.add(card(i)));
        return cards;
    }

    private static void assertDense(List<Application> ordered) {
        assertThat(ordered).extracting(Application::getBoardPosition)
                .containsExactlyElementsOf(IntStream.range(0, ordered.size()).boxed().toList());
    }

    @Test
    void placesTheCardAtTheIndexAndShiftsTheRest() {
        List<Application> others = column(3);
        Application moved = card(7);

        List<Application> result = BoardColumn.place(others, moved, 1);

        assertThat(result).containsExactly(others.get(0), moved, others.get(1), others.get(2));
        assertDense(result);
    }

    @Test
    void indexZeroPutsTheCardFirst() {
        List<Application> others = column(2);
        Application moved = card(0);

        List<Application> result = BoardColumn.place(others, moved, 0);

        assertThat(result).containsExactly(moved, others.get(0), others.get(1));
        assertDense(result);
    }

    @Test
    void anIndexBeyondTheEndPutsTheCardLast() {
        List<Application> others = column(2);
        Application moved = card(0);

        List<Application> result = BoardColumn.place(others, moved, 99);

        assertThat(result).containsExactly(others.get(0), others.get(1), moved);
        assertDense(result);
    }

    @Test
    void ignoresTheMovedCardIfItIsAlreadyInTheList() {
        List<Application> cards = column(3);
        Application moved = cards.get(0);

        List<Application> result = BoardColumn.place(cards, moved, 2);

        assertThat(result).containsExactly(cards.get(1), cards.get(2), moved);
        assertDense(result);
    }

    @Test
    void placesIntoAnEmptyColumn() {
        Application moved = card(5);

        assertThat(BoardColumn.place(List.of(), moved, 3)).containsExactly(moved);
        assertThat(moved.getBoardPosition()).isZero();
    }

    @Test
    void compactClosesTheGapsKeepingTheOrder() {
        List<Application> cards = List.of(card(0), card(2), card(5));

        BoardColumn.compact(cards);

        assertDense(cards);
    }

    @Test
    void rejectsANegativeIndex() {
        assertThatThrownBy(() -> BoardColumn.place(column(1), card(0), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
