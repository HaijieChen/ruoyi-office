import { ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { listEmployeeColleagues } from '#/api/hrm/employee';

export function useBusinessStaffField() {
  const userStore = useUserStore();
  const staffOptions = ref<{ label: string; value: number }[]>([]);

  function selfUserId(): number | undefined {
    const id = Number(userStore.userInfo?.id);
    return Number.isFinite(id) && id > 0 ? id : undefined;
  }

  async function loadBusinessStaff(): Promise<number | undefined> {
    const self = selfUserId();
    const list = (await listEmployeeColleagues()) || [];
    const opts = list
      .filter((item) => item.userId != null)
      .map((item) => ({
        value: Number(item.userId),
        label: item.name || String(item.userId),
      }));
    if (self && !opts.some((item) => item.value === self)) {
      const info = userStore.userInfo as { nickname?: string; realName?: string };
      opts.unshift({
        value: self,
        label: info?.nickname || info?.realName || String(self),
      });
    }
    staffOptions.value = opts;
    return self;
  }

  return { staffOptions, loadBusinessStaff, selfUserId };
}
