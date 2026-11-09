import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ScoreRing } from './ScoreRing';

describe('ScoreRing — puntuación circular (rojo < 50, ámbar 50–74, verde ≥ 75)', () => {
  it.each([
    [0, 'bajo', 'Encaje bajo'],
    [49, 'bajo', 'Encaje bajo'],
    [50, 'medio', 'Encaje medio'],
    [74, 'medio', 'Encaje medio'],
    [75, 'alto', 'Encaje alto'],
    [100, 'alto', 'Encaje alto'],
  ] as const)('%s → tono %s', (score, tone, label) => {
    render(<ScoreRing score={score} />);

    const ring = screen.getByTestId('analysis-score');
    expect(ring).toHaveAttribute('data-tone', tone);
    // El color no es la única pista: el número y el nivel van en texto.
    expect(ring).toHaveTextContent(String(score));
    expect(ring).toHaveTextContent(label);
    expect(ring).toHaveAccessibleName(`Encaje: ${score} de 100`);
  });

  it('el arco es proporcional a la puntuación', () => {
    render(<ScoreRing score={85} />);

    const arc = screen.getByTestId('analysis-score-arc');
    const length = Number(arc.getAttribute('stroke-dasharray'));
    expect(Number(arc.getAttribute('stroke-dashoffset'))).toBeCloseTo(length * 0.15);
  });
});
