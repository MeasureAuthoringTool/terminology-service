package gov.cms.madie.terminology.util;

/** Offset/count pagination cursor used while paging VSAC FHIR search and history bundles. */
public record PageCursor(Integer offset, Integer count) {}
