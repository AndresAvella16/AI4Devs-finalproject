import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MatchScoreBadge } from './MatchScoreBadge';
import { scoreTone } from './scoreTone';

describe('scoreTone (rojo < 50, ámbar 50–74, verde ≥ 75)', () => {
  it.each([
    [0, 'bajo'],
    [49, 'bajo'],
    [50, 'medio'],
    [74, 'medio'],
    [75, 'alto'],
    [100, 'alto'],
    [null, 'sin-analizar'],
  ] as const)('%s → %s', (score, tone) => {
    expect(scoreTone(score)).toBe(tone);
  });
});

describe('MatchScoreBadge', () => {
  it('muestra la puntuación con su color y un texto accesible', () => {
    render(<MatchScoreBadge score={85} />);

    const badge = screen.getByTestId('match-score-badge');
    expect(badge).toHaveTextContent('Encaje: 85 de 100');
    expect(badge).toHaveAttribute('data-tone', 'alto');
    expect(badge.className).toContain('green');
  });

  it.each([
    [42, 'red'],
    [68, 'amber'],
  ])('%s usa el color %s', (score, color) => {
    render(<MatchScoreBadge score={score} />);

    expect(screen.getByTestId('match-score-badge').className).toContain(color);
  });

  it('sin análisis muestra "Sin analizar" en neutro', () => {
    render(<MatchScoreBadge score={null} />);

    const badge = screen.getByTestId('match-score-badge');
    expect(badge).toHaveTextContent('Sin analizar');
    expect(badge).toHaveAttribute('data-tone', 'sin-analizar');
  });
});
