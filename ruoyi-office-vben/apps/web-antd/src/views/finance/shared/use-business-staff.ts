import { ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { listEmployeeColleagues } from '#/api/hrm/employee';

function isBlankLabel(label: string | undefined, userId: number) {
  const text = (label || '').trim();
  return !text || text === String(userId);
}

export function useBusinessStaffField() {
  const userStore = useUserStore();
  const staffOptions = ref<{ label: string; value: number }[]>([]);

  function selfUserId(): number | undefined {
    const id = Number(userStore.userInfo?.id);
    return Number.isFinite(id) && id > 0 ? id : undefined;
  }

  function selfLabel(userId: number): string {
    const info = userStore.userInfo as {
      name?: string;
      nickname?: string;
      realName?: string;
      username?: string;
    };
    const label =
      info?.nickname ||
      info?.realName ||
      info?.name ||
      info?.username ||
      '';
    return isBlankLabel(label, userId) ? '当前用户' : label;
  }

  async function loadBusinessStaff(): Promise<number | undefined> {
    const self = selfUserId();
    let list: { name?: string; userId?: number }[] = [];
    try {
      list = (await listEmployeeColleagues()) || [];
    } catch {
      list = [];
    }
    const opts = list
      .filter((item) => item.userId != null)
      .map((item) => {
        const value = Number(item.userId);
        const raw = (item.name || '').trim();
        return {
          value,
          label: isBlankLabel(raw, value) ? `用户${value}` : raw,
        };
      });
    if (self) {
      const existing = opts.find((item) => item.value === self);
      const label = selfLabel(self);
      if (existing) {
        if (isBlankLabel(existing.label, self) || existing.label.startsWith('用户')) {
          existing.label = label;
        }
      } else {
        opts.unshift({ value: self, label });
      }
    }
    staffOptions.value = opts;
    return self;
  }

  return { staffOptions, loadBusinessStaff, selfUserId };
}
