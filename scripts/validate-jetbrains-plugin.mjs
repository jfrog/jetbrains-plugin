#!/usr/bin/env node

// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

import { promises as fs } from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const errors = [];
const warnings = [];

function addError(message) {
  errors.push(message);
}

function addWarning(message) {
  warnings.push(message);
}

async function pathExists(targetPath) {
  try {
    await fs.access(targetPath);
    return true;
  } catch {
    return false;
  }
}

function normalizeNewlines(content) {
  return content.replace(/\r\n/g, "\n");
}

function extractFrontmatterBlock(content) {
  const normalized = normalizeNewlines(content);
  if (!normalized.startsWith("---\n")) {
    return null;
  }
  const closingIndex = normalized.indexOf("\n---\n", 4);
  if (closingIndex === -1) {
    return null;
  }
  return normalized.slice(4, closingIndex);
}

async function validateSkillFile(filePath) {
  const content = await fs.readFile(filePath, "utf8");
  const relativeFile = path.relative(repoRoot, filePath);
  const block = extractFrontmatterBlock(content);
  if (!block) {
    addError(`skill missing YAML frontmatter: ${relativeFile}`);
    return;
  }
  if (!/^name:\s+/m.test(block)) {
    addError(`skill missing "name" in frontmatter: ${relativeFile}`);
  }
  if (!/^description:\s+/m.test(block)) {
    addError(`skill missing "description" in frontmatter: ${relativeFile}`);
  }
}

async function validateSkills() {
  const skillsDir = path.join(repoRoot, ".junie", "skills");
  if (!(await pathExists(skillsDir))) {
    addError(`no .junie/skills/ directory found`);
    return;
  }
  const entries = await fs.readdir(skillsDir, { withFileTypes: true });
  let foundSkill = false;
  for (const entry of entries) {
    if (!entry.isDirectory()) {
      continue;
    }
    const skillMd = path.join(skillsDir, entry.name, "SKILL.md");
    if (await pathExists(skillMd)) {
      foundSkill = true;
      await validateSkillFile(skillMd);
    }
  }
  if (!foundSkill) {
    addError(`no .junie/skills/*/SKILL.md found under ${path.relative(repoRoot, skillsDir)}`);
  }
}

async function validatePluginXml() {
  const pluginXmlPath = path.join(repoRoot, "src", "main", "resources", "META-INF", "plugin.xml");
  if (!(await pathExists(pluginXmlPath))) {
    addError(`plugin manifest missing: ${path.relative(repoRoot, pluginXmlPath)}`);
    return;
  }
  const content = await fs.readFile(pluginXmlPath, "utf8");
  for (const tag of ["<id>", "<name>", "<vendor"]) {
    if (!content.includes(tag)) {
      addError(`plugin.xml missing required "${tag}" element`);
    }
  }
  if (!/<depends>com\.intellij\.modules\.platform<\/depends>/.test(content)) {
    addWarning(`plugin.xml does not depend on com.intellij.modules.platform - confirm this is intentional`);
  }
}

async function main() {
  await validatePluginXml();
  await validateSkills();
  summarizeAndExit();
}

function summarizeAndExit() {
  if (warnings.length > 0) {
    console.log("Warnings:");
    for (const warning of warnings) {
      console.log(`- ${warning}`);
    }
    console.log("");
  }

  if (errors.length > 0) {
    console.error("Validation failed:");
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }

  console.log("Validation passed.");
}

await main();
