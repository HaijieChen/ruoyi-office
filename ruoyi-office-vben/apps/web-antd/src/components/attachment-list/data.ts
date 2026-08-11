import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AttachmentApi } from '#/api/common/attachment';

import { ACTION_ICON } from '#/adapter/vxe-table';

/**
 * 格式化文件大小
 * @param size 文件大小（字节）
 * @returns 格式化后的文件大小字符串
 */
export function formatFileSize(size: number): string {
  if (size < 1024) return `${size}B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`;
  return `${(size / (1024 * 1024)).toFixed(1)}MB`;
}

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
        return formatFileSize(cellValue || 0);
      },
    },
    {
      field: 'fileExtension',
      title: '文件类型',
      width: 100,
      formatter: ({ cellValue }) => {
        return cellValue ? cellValue.toUpperCase() : '';
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

/**
 * 根据 /infra/file/upload-detail 权威 claim 创建附件元数据。
 * 必须携带 fileId；禁止 blob: URL。
 */
export function createAttachmentFromUpload(
  file: File,
  sortOrder: number,
  uploaded: { id: number; url: string; path?: string; size?: number; type?: string },
): AttachmentApi.AttachmentSaveReq & { fileId?: number; downloadPath?: string } {
  if (!uploaded?.id) {
    throw new Error('文件上传必须返回权威 fileId');
  }
  if (!uploaded.url || uploaded.url.startsWith('blob:')) {
    throw new Error('文件上传失败：未获得服务端文件地址');
  }
  const path = uploaded.path || uploaded.url;
  return {
    id: undefined,
    fileId: uploaded.id,
    businessType: '',
    businessId: 0,
    fileName: file.name,
    filePath: path,
    fileUrl: uploaded.url,
    fileSize: uploaded.size ?? file.size,
    fileType: uploaded.type ?? file.type,
    fileExtension: file.name.includes('.')
      ? file.name.split('.').pop()!.toLowerCase()
      : '',
    uploadTime: new Date(),
    sortOrder,
    remark: '',
  };
}

/**
 * @deprecated 使用 createAttachmentFromUpload
 */
export function createAttachment(
  file: File,
  sortOrder: number,
  uploaded?: { id: number; url: string; path?: string; size?: number; type?: string },
): AttachmentApi.AttachmentSaveReq {
  if (!uploaded?.id || !uploaded?.url) {
    throw new Error('必须先完成文件上传（upload-detail）再创建附件元数据');
  }
  return createAttachmentFromUpload(file, sortOrder, uploaded);
}
