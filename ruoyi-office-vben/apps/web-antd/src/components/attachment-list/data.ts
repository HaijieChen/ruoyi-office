import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AttachmentApi } from '#/api/common/attachment';

import { ACTION_ICON } from '#/adapter/vxe-table';

import { createAttachmentFromOnboardingClaim } from './onboarding-claim';
import { formatFileSize } from './format-file-size';

export { formatFileSize };

/**
 * 附件列表表格列配置
 * @param readonly 是否只读模式
 */
export function useAttachmentColumns(readonly: boolean = false): VxeTableGridOptions['columns'] {
  return [
    {
      type: 'seq',
      width: 60,
      title: '序号',
    },
    {
      field: 'fileName',
      title: '文件名',
      minWidth: 200,
      showOverflow: 'tooltip',
    },
    {
      field: 'fileSize',
      title: '文件大小',
      width: 120,
      formatter: ({ cellValue }) => {
        return formatFileSize(cellValue);
      },
    },
    {
      field: 'fileExtension',
      title: '文件类型',
      width: 100,
      formatter: ({ cellValue, row }) => {
        if (cellValue) return String(cellValue).toUpperCase();
        const type = String(row?.fileType || '').toLowerCase();
        if (type.includes('jpeg') || type.includes('jpg')) return 'JPG';
        if (type.includes('png')) return 'PNG';
        if (type.includes('pdf')) return 'PDF';
        if (type.includes('/')) return type.slice(type.lastIndexOf('/') + 1).toUpperCase();
        return '';
      },
    },
    {
      field: 'uploadTime',
      title: '上传时间',
      width: 160,
      formatter: ({ row }) => {
        // 如果 uploadTime 为 0 或空，则使用 createTime
        const time = row.uploadTime && row.uploadTime !== 0 ? row.uploadTime : row.createTime;
        if (!time) return '';
        
        // 格式化时间显示
        const date = new Date(time);
        return date.toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        });
      },
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 150,
      showOverflow: 'tooltip',
      // 只在非只读模式下启用编辑功能
      editRender: readonly ? undefined : {
        name: 'input',
        placeholder: '请输入备注',
      },
    },
    {
      title: '操作',
      width: 180,
      fixed: 'right',
      slots: {
        default: 'actions',
      },
    },
  ];
}

/**
 * 附件操作按钮配置
 */
export function useAttachmentActions(
  readonly: boolean,
  onPreview: () => void,
  onDownload: () => void,
  onDelete: () => void,
) {
  return [
    {
      label: '预览',
      type: 'link' as const,
      icon: ACTION_ICON.VIEW,
      onClick: onPreview,
    },
    {
      label: '下载',
      type: 'link' as const,
      icon: ACTION_ICON.DOWNLOAD,
      onClick: onDownload,
    },
    {
      label: '删除',
      type: 'link' as const,
      danger: true,
      icon: ACTION_ICON.DELETE,
      ifShow: () => !readonly,
      popConfirm: {
        title: '确定要删除这个附件吗？',
        confirm: onDelete,
      },
    },
  ];
}

export { createAttachmentFromOnboardingClaim } from './onboarding-claim';

/**
 * @deprecated 入职资料请用 createAttachmentFromOnboardingClaim
 */
export function createAttachmentFromUpload(
  file: File,
  sortOrder: number,
  uploaded: {
    id?: number;
    claimToken?: string;
    url?: string;
    path?: string;
    size?: number;
    type?: string;
    fileName?: string;
    fileSize?: number;
    fileExtension?: string;
    expireTime?: number;
  },
): AttachmentApi.AttachmentSaveReq & {
  claimToken?: string;
  fileId?: number;
  downloadPath?: string;
} {
  if (uploaded?.claimToken) {
    return createAttachmentFromOnboardingClaim(file, sortOrder, {
      claimToken: uploaded.claimToken,
      fileName: uploaded.fileName,
      fileSize: uploaded.fileSize ?? uploaded.size,
      fileExtension: uploaded.fileExtension,
      expireTime: uploaded.expireTime,
    });
  }
  throw new Error('入职资料必须使用 HRM claimToken，禁止 fileId/公开 URL');
}

/**
 * @deprecated 使用 createAttachmentFromOnboardingClaim
 */
export function createAttachment(
  file: File,
  sortOrder: number,
  uploaded?: {
    id?: number;
    claimToken?: string;
    url?: string;
    path?: string;
    size?: number;
    type?: string;
    fileName?: string;
    fileSize?: number;
    fileExtension?: string;
    expireTime?: number;
  },
): AttachmentApi.AttachmentSaveReq {
  if (!uploaded?.claimToken) {
    throw new Error('必须先完成入职资料上传（claim）再创建附件元数据');
  }
  return createAttachmentFromOnboardingClaim(file, sortOrder, {
    claimToken: uploaded.claimToken,
    fileName: uploaded.fileName,
    fileSize: uploaded.fileSize ?? uploaded.size,
    fileExtension: uploaded.fileExtension,
    expireTime: uploaded.expireTime,
  });
}
