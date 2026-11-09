import type { RequirementMatch } from '../../api/analysis';
import { isBlocking, MATCH_RESULT_LABELS, REQUIREMENT_TYPE_LABELS } from './labels';

/** Resultado de cada requisito, en el orden de la oferta. Los bloqueantes se resaltan y se etiquetan en texto. */
export function RequirementMatchTable({ matches }: { matches: RequirementMatch[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left text-sm">
        <caption className="sr-only">Resultado por requisito</caption>
        <thead>
          <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
            <th scope="col" className="py-2 pr-3 font-medium">
              Requisito
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              Resultado
            </th>
            <th scope="col" className="py-2 font-medium">
              Evidencia en tu perfil
            </th>
          </tr>
        </thead>
        <tbody>
          {matches.map((match) => {
            const blocking = isBlocking(match);
            const result = MATCH_RESULT_LABELS[match.result];
            return (
              <tr
                key={match.requirementId}
                data-testid="requirement-match-row"
                data-result={match.result}
                data-blocking={blocking}
                className={`border-b border-slate-100 align-top last:border-0 ${blocking ? 'bg-red-50' : ''}`}
              >
                <th scope="row" className="py-2 pr-3 font-normal text-slate-800">
                  <span className="block">{match.description}</span>
                  <span className="mt-1 flex flex-wrap gap-1">
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                      {REQUIREMENT_TYPE_LABELS[match.type]}
                    </span>
                    {blocking && (
                      <span className="rounded border border-red-300 bg-white px-2 py-0.5 text-xs font-semibold text-red-800">
                        Bloqueante
                      </span>
                    )}
                  </span>
                </th>
                <td className="py-2 pr-3">
                  <span
                    className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-semibold ${result.className}`}
                  >
                    <span aria-hidden="true">{result.icon}</span>
                    {result.label}
                  </span>
                </td>
                <td className="py-2 text-slate-700">
                  {match.evidence ?? <span className="italic text-slate-500">Sin evidencia en tu perfil</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
