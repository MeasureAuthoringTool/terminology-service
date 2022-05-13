package cms.gov.madie.terminology.dto;

import lombok.Data;

@Data
public class ResultSet{
  public String csName;
  public String csOID;
  public String csVersion;
  public String code;
  public String contentMode;
  public String codeName;
  public String termType;
  public String active;
  public long revision;
}
