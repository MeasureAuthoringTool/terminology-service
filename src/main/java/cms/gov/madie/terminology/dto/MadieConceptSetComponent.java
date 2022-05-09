package cms.gov.madie.terminology.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MadieConceptSetComponent {

  private String version;
  private String system;
  List<MadieConceptReferenceComponent> concept;
}
