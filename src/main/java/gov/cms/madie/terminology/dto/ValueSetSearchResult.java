package gov.cms.madie.terminology.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ValueSetSearchResult {
  List<ValueSetForSearch> valueSets;
  // Returning bundle as String because the FHIR Parser generates
  // a typed Bundle object that is excessively huge when serialized
  // since it initializes all values and arrays to null/empty array.
  String resultBundle;
}
