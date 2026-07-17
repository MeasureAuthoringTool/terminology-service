package gov.cms.madie.terminology.util;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

public final class PagingUtil {

  private PagingUtil() {}

  /**
   * Build a Pageable from page/limit/sortInfo. sortInfo format: "sortField,descFlag" (e.g.
   * "name,false"). If sortInfo is blank or malformed, falls back to sorting by title desc.
   *
   * @param page page number
   * @param limit page size
   * @param sortInfo sort instruction
   * @param defaultField default sort field
   * @param mapSortField mapper from UI label to entity field (e.g. "FHIR Version" ->
   *     "version.fhirVersion")
   * @return pageable request
   */
  public static Pageable buildPageable(
      int page,
      int limit,
      String sortInfo,
      String defaultField,
      Function<String, String> mapSortField) {
    if (StringUtils.isNotBlank(sortInfo)) {
      String[] parts = sortInfo.split(",");
      if (parts.length == 2) {
        String sortBy = mapSortField.apply(parts[0]);
        boolean desc = Boolean.parseBoolean(parts[1]);
        Sort.Order order = desc ? Sort.Order.desc(sortBy) : Sort.Order.asc(sortBy);
        return PageRequest.of(page, limit, Sort.by(order));
      }
    }
    return PageRequest.of(page, limit, Sort.by(Sort.Order.desc(defaultField)));
  }
}
