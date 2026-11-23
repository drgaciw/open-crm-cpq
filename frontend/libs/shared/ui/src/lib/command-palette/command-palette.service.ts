import { DOCUMENT } from '@angular/common';
import { Injectable, Injector, inject, signal } from '@angular/core';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { Platform } from '@angular/cdk/platform';
import { Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import {
  COMMAND_PALETTE_DATA,
  CommandPaletteComponent,
  CommandPaletteData,
  CommandPaletteItem,
} from './command-palette.component';

export interface CommandPaletteRef {
  close: () => void;
  afterClosed: () => Observable<CommandPaletteItem | null>;
}

/**
 * Opens and manages the global CDK Overlay command palette.
 */
@Injectable({ providedIn: 'root' })
export class CommandPaletteService {
  private readonly overlay = inject(Overlay);
  private readonly injector = inject(Injector);
  private readonly platform = inject(Platform);
  private readonly router = inject(Router, { optional: true });
  private readonly document = inject(DOCUMENT, { optional: true });

  private readonly openState = signal(false);
  readonly isOpen = this.openState.asReadonly();

  private activeOverlayRef: OverlayRef | null = null;
  private activeClosedSubject: Subject<CommandPaletteItem | null> | null = null;

  /** Returns `'⌘K'` on macOS/iOS and `'Ctrl+K'` on other platforms. */
  shortcutLabel(): string {
    return this.isApplePlatform() ? '⌘K' : 'Ctrl+K';
  }

  open(items: CommandPaletteItem[]): CommandPaletteRef {
    this.close();

    const closedSubject = new Subject<CommandPaletteItem | null>();
    this.activeClosedSubject = closedSubject;

    const config = new OverlayConfig({
      hasBackdrop: true,
      backdropClass: 'cdk-overlay-dark-backdrop',
      scrollStrategy: this.overlay.scrollStrategies.block(),
      positionStrategy: this.overlay
        .position()
        .global()
        .centerHorizontally()
        .top('12vh'),
    });

    const overlayRef = this.overlay.create(config);
    this.activeOverlayRef = overlayRef;
    this.openState.set(true);

    const finish = (selected: CommandPaletteItem | null): void => {
      if (this.activeOverlayRef !== overlayRef) {
        return;
      }
      this.activeOverlayRef = null;
      this.activeClosedSubject = null;
      this.openState.set(false);
      overlayRef.dispose();
      closedSubject.next(selected);
      closedSubject.complete();
    };

    const data: CommandPaletteData = {
      items,
      onSelect: (item) => {
        finish(item);
        if (item.action) {
          item.action();
        } else if (item.route && this.router) {
          void this.router.navigate(item.route);
        }
      },
      onClose: () => finish(null),
    };

    const portalInjector = Injector.create({
      parent: this.injector,
      providers: [{ provide: COMMAND_PALETTE_DATA, useValue: data }],
    });

    const portal = new ComponentPortal(CommandPaletteComponent, null, portalInjector);
    const componentRef = overlayRef.attach(portal);
    componentRef.changeDetectorRef.detectChanges();
    componentRef.instance.focusInput();

    overlayRef.backdropClick().subscribe(() => finish(null));
    overlayRef.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        finish(null);
      }
    });

    return {
      close: () => finish(null),
      afterClosed: () => closedSubject.asObservable(),
    };
  }

  close(): void {
    const overlayRef = this.activeOverlayRef;
    const closedSubject = this.activeClosedSubject;
    if (!overlayRef) {
      return;
    }
    this.activeOverlayRef = null;
    this.activeClosedSubject = null;
    this.openState.set(false);
    overlayRef.dispose();
    closedSubject?.next(null);
    closedSubject?.complete();
  }

  private isApplePlatform(): boolean {
    if (this.platform.IOS) {
      return true;
    }
    if (!this.platform.isBrowser) {
      return false;
    }
    const nav = this.document?.defaultView?.navigator;
    const platformString = nav?.platform ?? '';
    const userAgent = nav?.userAgent ?? '';
    return /Mac|iPhone|iPad|iPod/i.test(platformString) || /Macintosh|Mac OS X/i.test(userAgent);
  }
}
