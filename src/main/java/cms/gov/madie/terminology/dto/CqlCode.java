package cms.gov.madie.terminology.dto;

import lombok.Data;

@Data
public class CqlCode extends CqlText {
  String codeId;
  String codeSystem;
  int hits;
}
