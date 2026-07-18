import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace BpmFormDataSourceApi {
  export const SourceType = {
    DICT: 2,
    PLATFORM_API: 3,
    SQL: 1,
  } as const;

  export const VersionStatus = {
    DRAFT: 1,
    PUBLISHED: 0,
  } as const;

  export interface Definition {
    code: string;
    createTime?: string;
    id: number;
    name: string;
    publishedVersion?: number;
    status: number;
    type: number;
  }

  export interface DefinitionSaveReq {
    code: string;
    id?: number;
    name: string;
    type: number;
  }

  export interface CreateWithDraftReq {
    definition: DefinitionSaveReq;
    version: VersionSaveReq;
  }

  export interface PageReq extends PageParam {
    code?: string;
    createTime?: string[];
    name?: string;
    status?: number;
    type?: number;
  }

  export interface SchemaField {
    mask?: string;
    name: string;
    required?: boolean;
    type: string;
  }

  export interface VersionSaveReq {
    cacheSeconds?: number;
    labelField?: string;
    maxRows?: number;
    pageable: boolean;
    parameterSchema?: string;
    resultSchema?: string;
    sourceConfig: string;
    timeoutSeconds?: number;
    valueField?: string;
  }

  export interface VersionSummary {
    createTime: string;
    dataSourceId: number;
    id: number;
    status: number;
    updateTime: string;
    version: number;
  }

  /** Full configuration is available only to administrators with update permission. */
  export interface Version extends VersionSaveReq, VersionSummary {}

  export interface TrialRunReq {
    params: Record<string, unknown>;
    sourceId: number;
    versionId: number;
  }

  export interface ExecuteResult {
    rows: Record<string, unknown>[];
    total: number;
    version: number;
  }
}

export function getDataSourcePage(params: BpmFormDataSourceApi.PageReq) {
  return requestClient.get<PageResult<BpmFormDataSourceApi.Definition>>(
    '/bpm/form-data-source/page',
    { params },
  );
}

export function getDataSource(id: number) {
  return requestClient.get<BpmFormDataSourceApi.Definition>(
    '/bpm/form-data-source/get',
    { params: { id } },
  );
}

export function createDataSource(data: BpmFormDataSourceApi.DefinitionSaveReq) {
  return requestClient.post<number>('/bpm/form-data-source/create', data);
}

/** Creates the definition and its first draft in one backend transaction. */
export function createDataSourceWithDraft(
  data: BpmFormDataSourceApi.CreateWithDraftReq,
) {
  return requestClient.post<number>(
    '/bpm/form-data-source/create-with-draft',
    data,
  );
}

export function updateDataSource(data: BpmFormDataSourceApi.DefinitionSaveReq) {
  return requestClient.put<boolean>('/bpm/form-data-source/update', data);
}

export function getDataSourceSimpleList() {
  return requestClient.get<BpmFormDataSourceApi.Definition[]>(
    '/bpm/form-data-source/simple-list',
  );
}

/** Returns metadata only and intentionally excludes sourceConfig and schemas. */
export function getDataSourceVersionList(sourceId: number) {
  return requestClient.get<BpmFormDataSourceApi.VersionSummary[]>(
    '/bpm/form-data-source/version/list',
    { params: { sourceId } },
  );
}

/** Requires bpm:form-data-source:update. Do not call from the list screen. */
export function getDataSourceVersion(sourceId: number, versionId: number) {
  return requestClient.get<BpmFormDataSourceApi.Version>(
    '/bpm/form-data-source/version/get',
    { params: { sourceId, versionId } },
  );
}

export function saveDataSourceDraft(
  sourceId: number,
  data: BpmFormDataSourceApi.VersionSaveReq,
) {
  return requestClient.post<number>(
    '/bpm/form-data-source/version/save-draft',
    data,
    { params: { sourceId } },
  );
}

export function trialRunDataSource(data: BpmFormDataSourceApi.TrialRunReq) {
  return requestClient.post<BpmFormDataSourceApi.ExecuteResult>(
    '/bpm/form-data-source/version/trial-run',
    data,
  );
}

export function publishDataSource(sourceId: number, versionId: number) {
  return requestClient.post<boolean>(
    '/bpm/form-data-source/version/publish',
    null,
    { params: { sourceId, versionId } },
  );
}

export function disableDataSource(id: number) {
  return requestClient.put<boolean>('/bpm/form-data-source/disable', null, {
    params: { id },
  });
}
