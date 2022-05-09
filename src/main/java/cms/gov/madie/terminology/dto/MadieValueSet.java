package cms.gov.madie.terminology.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MadieValueSet {

  private String ID;
  private String url;
  private String name;
  private String title;
  private String version;
  private String status;
  private Date date;
  private String publisher;
  private String description;
  private String purpose;

  private MadieValueSetComposeComponent compose;
}
