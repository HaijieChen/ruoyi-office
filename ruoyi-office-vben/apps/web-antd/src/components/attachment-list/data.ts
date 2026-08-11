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
 * 根据已成功上传到文件服务的结果创建附件元数据。
 * fileUrl 必须是服务端返回的持久化访问地址，禁止使用 blob: 本地 URL。
 */
export function createAttachmentFromUpload(
  file: File,
  sortOrder: number,
  uploaded: { url: string; path?: string },
): AttachmentApi.AttachmentSaveReq {
  const url = uploaded.url;
  // path：优先服务端 path；否则从 URL 路径段推导（去掉 query）
  let path = uploaded.path;
  if (!path) {
    try {
      const u = new URL(url, 'http://local.invalid');
      path = u.pathname || url;
    } catch {
      path = url;
    }
  }
  return {
    id: undefined,
    businessType: '',
    businessId: 0,
    fileName: file.name,
    filePath: path,
    fileUrl: url,
    fileSize: file.size,
    fileType: file.type,
    fileExtension: file.name.includes('.')
      ? file.name.split('.').pop()!.toLowerCase()
      : '',
    uploadTime: new Date(),
    sortOrder,
    remark: '',
  };
}

/**
 * @deprecated 使用 createAttachmentFromUpload；保留别名避免外部引用断裂
 */
export function createAttachment(
  file: File,
  sortOrder: number,
  uploaded?: { url: string; path?: string },
): AttachmentApi.AttachmentSaveReq {
  if (!uploaded?.url) {
    throw new Error('必须先完成文件上传再创建附件元数据');
  }
  return createAttachmentFromUpload(file, sortOrder, uploaded);
}
