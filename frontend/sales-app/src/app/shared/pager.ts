import { computed, signal } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';

/**
 * Lightweight, signal-based client-side pager for Material tables.
 *
 * Usage:
 *   pager = createPager(() => this.items());     // source is a signal/getter returning the full list
 *   // template: [dataSource]="pager.paged()" and a <mat-paginator>
 *
 * The page index is clamped to the available range so filtering/reloading
 * never leaves the table on an empty page.
 */
export function createPager<T>(source: () => T[], initialSize = 5) {
  const pageIndex = signal(0);
  const pageSize = signal(initialSize);

  const total = computed(() => source().length);

  const paged = computed<T[]>(() => {
    const list = source();
    const size = pageSize();
    const maxIndex = Math.max(0, Math.ceil(list.length / size) - 1);
    const idx = Math.min(pageIndex(), maxIndex);
    const start = idx * size;
    return list.slice(start, start + size);
  });

  const onPage = (e: PageEvent) => {
    pageIndex.set(e.pageIndex);
    pageSize.set(e.pageSize);
  };

  const reset = () => pageIndex.set(0);

  return { pageIndex, pageSize, total, paged, onPage, reset };
}

/**
 * Server-side pager: the component fetches one page at a time from the API.
 * Holds page index/size and the server-reported total, and invokes `reload`
 * whenever the user changes page or page size.
 *
 * Usage:
 *   pager = createServerPager(() => this.load());
 *   // after each fetch: pager.total.set(res.total) and bind the table to the page items
 *   // template: [dataSource]="items()" with a <mat-paginator [length]="pager.total()" ...>
 */
export function createServerPager(reload: () => void, initialSize = 5) {
  const pageIndex = signal(0);
  const pageSize = signal(initialSize);
  const total = signal(0);

  const onPage = (e: PageEvent) => {
    pageIndex.set(e.pageIndex);
    pageSize.set(e.pageSize);
    reload();
  };

  const reset = () => pageIndex.set(0);

  return { pageIndex, pageSize, total, onPage, reset };
}

export const PAGE_SIZE_OPTIONS = [5, 10, 25, 50];
