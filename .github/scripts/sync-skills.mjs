#!/usr/bin/env node

// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

// Vendors skill content from the upstream jfrog/jfrog-skills repository
// into this plugin. Run manually when bumping the pin: bump `pin` in
// sync-skills-vendor.json, then run this
// script to regenerate .junie/skills/, then commit both alongside each other.
//
// Usage:
//   node .github/scripts/sync-skills.mjs
//
// Steps the script performs:
//   1. Reads sync-skills-vendor.json to learn which repo + ref to pull.
//   2. Downloads that tarball from codeload.github.com (public, no auth).
//   3. Extracts it into a temp directory.
//   4. Copies each requested upstream path to its mapped destination path,
//      replacing any existing tree.
//
// Unlike the other JFrog plugin repos (which vendor upstream `skills/` to a
// same-named `skills/` at the repo root), this repo renames the destination
// to `.junie/skills/` to match Junie's own discovery convention - hence
// `mappings` (from/to pairs) instead of the other repos' flat `paths` list.
//
// The pin in sync-skills-vendor.json is the single source of truth -
// there is no runtime override. To ship a different skill version,
// change the pin in a PR and commit the synced tree alongside it.

import { promises as fs, createWriteStream } from "node:fs";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

// filesystem helpers
async function readJson(filePath) {
  return JSON.parse(await fs.readFile(filePath, "utf8"));
}

async function fileExists(filePath) {
  try { await fs.access(filePath); return true; } catch { return false; }
}

// download the upstream tarball

// codeload.github.com serves any public repo's archive over HTTPS
// without auth, accepting a tag, branch, or commit SHA as the ref.
async function downloadTarball(repo, ref, destPath) {
  const url = `https://codeload.github.com/${repo}/tar.gz/${encodeURIComponent(ref)}`;
  const res = await fetch(url, { redirect: "follow" });
  if (!res.ok) throw new Error(`Could not download ${repo}@${ref} (HTTP ${res.status})`);
  await pipeline(Readable.fromWeb(res.body), createWriteStream(destPath));
  console.log(`  fetched ${url}`);
}

// extract the tarball

// Shells out to the system `tar` instead of pulling in an npm tar library —
// keeps the script zero-dependency.
//
// GitHub tarballs always have exactly one top-level directory whose
// name encodes the repo + commit. We return that path so the caller
// knows where to find the extracted tree.
async function extractTarball(tarballPath, intoDir) {
  await fs.mkdir(intoDir, { recursive: true });
  const result = spawnSync("tar", ["-xzf", tarballPath, "-C", intoDir], { stdio: "inherit" });
  if (result.status !== 0) throw new Error(`tar exited with status ${result.status}`);
  const [topLevel] = await fs.readdir(intoDir);
  return path.join(intoDir, topLevel);
}

// copy one upstream path to its mapped destination in the plugin

// Removes the destination first so we never end up with stale leftovers
// from a previous sync, then creates the destination's parent directory then copies.
async function copyMapping(fromDir, toDir, from, to) {
  const fromPath = path.join(fromDir, from);
  const toPath = path.join(toDir, to);
  if (!(await fileExists(fromPath))) {
    throw new Error(`path missing in upstream tarball: ${from}`);
  }
  await fs.rm(toPath, { recursive: true, force: true });
  await fs.mkdir(path.dirname(toPath), { recursive: true });
  await fs.cp(fromPath, toPath, { recursive: true });
  console.log(`  ${from} -> ${path.relative(process.cwd(), toPath)}`);
}

// Sync this plugin: read sync-skills-vendor.json, download + extract + copy.
//
// Paths are resolved relative to the script itself rather than CWD, so
// the script works regardless of where it's invoked from. The repo root
// is two levels up from .github/scripts/.
async function main() {
  const scriptDir = path.dirname(fileURLToPath(import.meta.url));
  const repoRoot = path.resolve(scriptDir, "..", "..");
  const vendorPath = path.join(scriptDir, "sync-skills-vendor.json");
  if (!(await fileExists(vendorPath))) {
    throw new Error(`missing sync-skills-vendor.json at ${vendorPath}`);
  }

  const { repo, pin, mappings } = await readJson(vendorPath);
  if (!repo || !pin || !Array.isArray(mappings) || mappings.length === 0) {
    throw new Error(`${vendorPath} must define 'repo', 'pin' and a non-empty 'mappings' array`);
  }

  console.log(`--- ${repo} (ref: ${pin}) ---`);

  const workDir = await fs.mkdtemp(path.join(tmpdir(), "sync-skills-"));
  try {
    // `slug` is just a unique filename for this tarball + extract dir.
    const slug = `${repo.replace("/", "-")}-${pin.replace(/[^A-Za-z0-9._-]/g, "_")}`;
    const tarball = path.join(workDir, `${slug}.tar.gz`);
    await downloadTarball(repo, pin, tarball);
    const extracted = await extractTarball(tarball, path.join(workDir, slug));
    for (const { from, to } of mappings) await copyMapping(extracted, repoRoot, from, to);
  } finally {
    await fs.rm(workDir, { recursive: true, force: true });
  }
  console.log("done.");
}

await main();
