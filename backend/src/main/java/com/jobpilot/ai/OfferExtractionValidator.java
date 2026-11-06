package com.jobpilot.ai;

import java.util.ArrayList;
import java.util.List;

/** Reglas de {@link OfferExtraction} que Bean Validation no expresa: la coherencia del salario. */
class OfferExtractionValidator implements ResponseValidator<OfferExtraction> {

    @Override
    public List<String> validate(OfferExtraction extraction) {
        List<String> problems = new ArrayList<>();
        OfferExtraction.Salary salary = extraction.salary();
        if (salary != null) {
            if (salary.min() == null && salary.max() == null) {
                problems.add("salary: sin mínimo ni máximo (debe ser null)");
            } else if (salary.min() != null && salary.max() != null && salary.max() < salary.min()) {
                problems.add("salary: el máximo es menor que el mínimo");
            }
        }
        return problems;
    }
}
