import type { AttachmentApi } from '#/api/common/attachment';

/**
 * 根据 HRM 入职资料 upload claim 创建附件元数据。
 * 仅持有 claimToken；禁止 fileId / 公开 URL / path / configId。
 */
export function createAttachmentFromOnboardingClaim(
  file: File,
  sortOrder: number,
  claim: {
    claimToken: string;
    fileName?: string;
    fileSize?: number;
    fileExtension?: string;
    expireTime?: number;
  },
): AttachmentApi.AttachmentSaveReq & {
  claimToken?: string;
  downloadPath?: string;
} {
  if (!claim?.claimToken) {
    throw new Error('文件上传必须返回作用域 claimToken');
  }
  return {
    id: undefined,
    claimToken: claim.claimToken,
    businessType: '',
    businessId: 0,
    fileName: claim.fileName || file.name,
    filePath: '',
    fileUrl: '',
    fileSize: claim.fileSize ?? file.size,
    fileType: file.type,
    fileExtension:
      claim.fileExtension ||
      (file.name.includes('.')
        ? file.name.split('.').pop()!.toLowerCase()
        : ''),
    uploadTime: new Date(),
    sortOrder,
    remark: '',
  } as any;
}
