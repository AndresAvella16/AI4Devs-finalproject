package com.jobpilot.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reglas de {@link MatchEvaluation} que Bean Validation no expresa: cada requisito enviado aparece exactamente una
 * vez (ninguno falta, ninguno se repite, ninguno es desconocido) y hay entre 3 y 5 sugerencias. Los problemas solo
 * nombran ids y recuentos, nunca el texto de la respuesta.
 */
class MatchEvaluationValidator implements ResponseValidator<MatchEvaluation> {

    static final int MIN_SUGGESTIONS = 3;
    static final int MAX_SUGGESTIONS = 5;

    private final Set<UUID> expected;

    MatchEvaluationValidator(Collection<UUID> requirementIds) {
        this.expected = Set.copyOf(requirementIds);
    }

    @Override
    public List<String> validate(MatchEvaluation evaluation) {
        List<String> problems = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Set<UUID> repeated = new LinkedHashSet<>();
        Set<UUID> unknown = new LinkedHashSet<>();
        for (MatchEvaluation.Match match : evaluation.matches()) {
            UUID id = match.requirementId();
            if (!expected.contains(id)) {
                unknown.add(id);
            } else if (!seen.add(id)) {
                repeated.add(id);
            }
        }
        Set<UUID> missing = new LinkedHashSet<>(expected);
        missing.removeAll(seen);

        if (!missing.isEmpty()) {
            problems.add("matches: falta el resultado de los requisitos " + missing);
        }
        if (!repeated.isEmpty()) {
            problems.add("matches: requisito repetido " + repeated);
        }
        if (!unknown.isEmpty()) {
            problems.add("matches: requisito desconocido " + unknown);
        }
        int suggestions = evaluation.suggestions().size();
        if (suggestions < MIN_SUGGESTIONS || suggestions > MAX_SUGGESTIONS) {
            problems.add("suggestions: " + suggestions + " sugerencias (deben ser entre " + MIN_SUGGESTIONS + " y "
                    + MAX_SUGGESTIONS + ")");
        }
        return problems;
    }
}
