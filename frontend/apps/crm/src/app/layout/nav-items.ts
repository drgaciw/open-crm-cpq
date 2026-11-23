import { CommandPaletteItem } from '@frontend/shared-ui';

export type NavGroup = 'CRM' | 'CPQ';

export interface NavItem {
  readonly id: string;
  readonly group: NavGroup;
  readonly label: string;
  readonly route: string;
  readonly icon: string;
  readonly keywords?: readonly string[];
  readonly paletteLabel?: string;
}

export const NAV_GROUPS: readonly NavGroup[] = ['CRM', 'CPQ'];

export const NAV_ITEMS: readonly NavItem[] = [
  {
    id: 'nav-dashboard',
    group: 'CRM',
    label: 'Dashboard',
    route: '/dashboard',
    icon: 'dashboard',
    keywords: ['home', 'overview'],
  },
  {
    id: 'nav-accounts',
    group: 'CRM',
    label: 'Accounts',
    route: '/organizations',
    icon: 'business',
    keywords: ['organizations', 'companies', 'customers'],
  },
  {
    id: 'nav-contacts',
    group: 'CRM',
    label: 'Contacts',
    route: '/contacts',
    icon: 'people',
    keywords: ['people', 'person'],
  },
  {
    id: 'nav-leads',
    group: 'CRM',
    label: 'Leads',
    route: '/leads',
    icon: 'leaderboard',
    keywords: ['prospects'],
  },
  {
    id: 'nav-opportunities',
    group: 'CRM',
    label: 'Opportunities',
    route: '/deals',
    icon: 'handshake',
    keywords: ['deals', 'pipeline'],
  },
  {
    id: 'nav-catalog',
    group: 'CPQ',
    label: 'Catalog',
    route: '/catalog',
    icon: 'inventory_2',
    keywords: ['products', 'sku'],
  },
  {
    id: 'nav-quotes',
    group: 'CPQ',
    label: 'Quotes',
    route: '/quotes',
    icon: 'request_quote',
    keywords: ['proposals', 'cpq'],
  },
  {
    id: 'nav-approvals',
    group: 'CPQ',
    label: 'Approvals',
    route: '/approvals',
    icon: 'fact_check',
    keywords: ['deal desk', 'inbox', 'review'],
  },
  {
    id: 'nav-contracts',
    group: 'CPQ',
    label: 'Contracts',
    route: '/contracts',
    icon: 'description',
    keywords: ['agreements', 'subscriptions', 'renewals'],
  },
  {
    id: 'nav-assets',
    group: 'CPQ',
    label: 'Assets',
    route: '/assets',
    icon: 'devices',
    keywords: ['entitlements', 'installed base'],
  },
  {
    id: 'nav-price-books',
    group: 'CPQ',
    label: 'Price Books',
    route: '/price-books',
    icon: 'menu_book',
    keywords: ['pricing', 'pricebooks', 'lists'],
  },
  {
    id: 'nav-pricing-customers',
    group: 'CPQ',
    label: 'Pricing Customers',
    route: '/pricing-customers',
    icon: 'groups',
    keywords: ['customers', 'accounts', 'pricing'],
  },
  {
    id: 'nav-rule-sets',
    group: 'CPQ',
    label: 'Rule Sets',
    route: '/rule-sets',
    icon: 'rule',
    keywords: ['rules', 'pricing rules', 'configuration'],
  },
  {
    id: 'nav-product-bundles',
    group: 'CPQ',
    label: 'Bundles',
    paletteLabel: 'Product Bundles',
    route: '/product-bundles',
    icon: 'inventory',
    keywords: ['bundles', 'packages', 'options'],
  },
  {
    id: 'nav-pricing-simulator',
    group: 'CPQ',
    label: 'Pricing Simulator',
    route: '/pricing/simulator',
    icon: 'science',
    keywords: ['simulate', 'bulk', 'pricing'],
  },
];

export function buildNavPaletteCommands(
  items: readonly NavItem[] = NAV_ITEMS,
): CommandPaletteItem[] {
  return items.map((item) => ({
    id: item.id,
    label: item.paletteLabel ?? item.label,
    category: item.group,
    icon: item.icon,
    keywords: item.keywords ? [...item.keywords] : undefined,
    route: [item.route],
  }));
}
