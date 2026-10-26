import { test } from '@playwright/test';
import ExcelJS from 'exceljs';
import { readdir, stat } from 'node:fs/promises';
import path from 'node:path';

const maxAgeMs = 30 * 60 * 1000;
const excelDirectory = path.resolve(process.env.EXCEL_DIR ?? 'excel-files');
const supportedExtensions = new Set(['.xlsx', '.xlsm']);

test('prints contents of the most recently modified Excel file from the last 30 minutes', async () => {
  let entries;
  try {
    entries = await readdir(excelDirectory, { withFileTypes: true });
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') {
      throw new Error(
        `Excel folder not found: ${excelDirectory}. Create it or set EXCEL_DIR to the folder containing your Excel files.`,
      );
    }
    throw error;
  }

  const recentFiles = await Promise.all(
    entries
      .filter((entry) => entry.isFile() && supportedExtensions.has(path.extname(entry.name).toLowerCase()))
      .map(async (entry) => {
        const filePath = path.join(excelDirectory, entry.name);
        const fileStat = await stat(filePath);
        return { filePath, modifiedAt: fileStat.mtime };
      }),
  );

  const cutoff = Date.now() - maxAgeMs;
  const latestFile = recentFiles
    .filter(({ modifiedAt }) => modifiedAt.getTime() >= cutoff && modifiedAt.getTime() <= Date.now())
    .sort((first, second) => second.modifiedAt.getTime() - first.modifiedAt.getTime())[0];

  if (!latestFile) {
    throw new Error(`No .xlsx or .xlsm file modified within the last 30 minutes was found in: ${excelDirectory}`);
  }

  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.readFile(latestFile.filePath);

  console.log(`Reading: ${latestFile.filePath}`);
  for (const worksheet of workbook.worksheets) {
    console.log(`Sheet: ${worksheet.name}`);
    worksheet.eachRow((row, rowNumber) => {
      console.log(`Row ${rowNumber}: ${JSON.stringify(row.values.slice(1))}`);
    });
  }
});