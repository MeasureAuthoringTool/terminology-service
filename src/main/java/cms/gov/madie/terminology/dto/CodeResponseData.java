package cms.gov.madie.terminology.dto;

import lombok.Data;

import java.util.ArrayList;

@Data
public class CodeResponseData {
  public int resultCount;
  public ArrayList<ResultSet> resultSet;
}
