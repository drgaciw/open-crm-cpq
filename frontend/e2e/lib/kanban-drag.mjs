/**
 * Perform a real pointer drag for Angular CDK Drag & Drop (pointer/mouse events, not HTML5 DnD).
 * Handles horizontal scrolling inside `.kanban-board` while holding the drag active so off-screen
 * columns (WON / LOST) can be targeted cleanly.
 */
export async function cdkDragTo(page, sourceLocator, targetLocator) {
  await sourceLocator.waitFor({ state: 'visible', timeout: 10000 });
  await sourceLocator.scrollIntoViewIfNeeded();
  const src = await sourceLocator.boundingBox();
  if (!src) {
    throw new Error('Source bounding box unavailable for CDK drag-and-drop');
  }
  const startX = src.x + src.width / 2;
  const startY = src.y + Math.min(24, src.height / 2);

  await page.mouse.move(startX, startY);
  await page.mouse.down();
  await page.mouse.move(startX + 12, startY + 4, { steps: 4 });

  // Bring target column into view if it is horizontally scrolled off-screen
  await targetLocator.scrollIntoViewIfNeeded();
  await page.waitForTimeout(100);
  const dst = await targetLocator.boundingBox();
  if (!dst) {
    await page.mouse.up();
    throw new Error('Target bounding box unavailable for CDK drag-and-drop');
  }
  const endX = dst.x + dst.width / 2;
  const endY = Math.max(dst.y + 40, Math.min(dst.y + 120, dst.y + dst.height / 2));

  await page.mouse.move(endX, endY, { steps: 16 });
  await page.waitForTimeout(180);
  await page.mouse.up();
  await page.waitForTimeout(300);
}
