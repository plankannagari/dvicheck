// TODO: replace with real calls once backend Bill endpoints exist
// (backend only has the Bill/LineItem entities + migration so far — no controller/service yet).
// Expected real endpoint: GET /bills/dashboard-summary

const MOCK_RECENT_BILLS = [
  { id: '1', storeName: 'Whole Foods', billType: 'GROCERY', purchaseDate: '2026-07-29', totalAmount: 84.32 },
  { id: '2', storeName: 'PG&E', billType: 'UTILITY', purchaseDate: '2026-07-27', totalAmount: 142.10 },
  { id: '3', storeName: "Trader Joe's", billType: 'GROCERY', purchaseDate: '2026-07-24', totalAmount: 56.78 },
  { id: '4', storeName: 'Target', billType: 'OTHER', purchaseDate: '2026-07-20', totalAmount: 39.99 },
];

const MOCK_SUMMARY = {
  monthTotal: 412.55,
  monthDeltaPercent: 8,
  billsScanned: 6,
  avoidableSpend: 47.2,
  avgBillAmount: 68.76,
  topSuggestion: {
    title: 'Coffee pods, 4 purchases this month',
    detail: 'Buying a larger pack could save you about $18/month.',
    savingEstimate: 18.0,
  },
  recentBills: MOCK_RECENT_BILLS,
};

export async function getDashboardSummary() {
  await new Promise((resolve) => setTimeout(resolve, 400));
  return MOCK_SUMMARY;
}
