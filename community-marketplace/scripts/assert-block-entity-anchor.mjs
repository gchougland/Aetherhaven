/**
 * Offline check that the Update 6 block-entity anchor shift matches
 * BlockEntityScaleMigrationTest / PositionMigrationSystem.
 *
 * Mirrors PrefabMeshBuilder.applyBlockEntityAnchorShift for identity rotation
 * (same cases as the Java unit tests).
 *
 * Usage: node scripts/assert-block-entity-anchor.mjs
 */

/**
 * @param {{ x: number, y: number, z: number }} position
 * @param {number} oldScale
 */
function applyAnchorShiftIdentity(position, oldScale) {
  position.y += oldScale / 4 - 0.5;
  position.y += 0.5;
}

function assertClose(label, actual, expected, eps = 1e-6) {
  if (Math.abs(actual - expected) > eps) {
    throw new Error(`${label}: expected ${expected}, got ${actual}`);
  }
}

function check(oldScale, startY, expectedY) {
  const position = { x: 10, y: startY, z: 20 };
  applyAnchorShiftIdentity(position, oldScale);
  assertClose(`scale ${oldScale} x`, position.x, 10);
  assertClose(`scale ${oldScale} y`, position.y, expectedY);
  assertClose(`scale ${oldScale} z`, position.z, 20);
}

check(2.0, 5.0, 5.5);
check(1.2, 2.0, 2.3);

console.log("assert-block-entity-anchor: ok");
