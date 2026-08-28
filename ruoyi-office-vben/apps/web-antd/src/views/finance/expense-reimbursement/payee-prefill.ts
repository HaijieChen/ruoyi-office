export interface WageCard {
  name?: string;
  bankName?: string;
  bankAccount?: string;
}

export function mapWageCardToPayee(card?: WageCard | null) {
  return {
    payeeAccountName: card?.name ?? '',
    payeeBankName: card?.bankName ?? '',
    payeeAccountNo: card?.bankAccount ?? '',
  };
}
