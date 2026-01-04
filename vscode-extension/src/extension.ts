/**
 * gbkt VS Code Extension
 *
 * Lightweight syntax support and build integration for gbkt (Game Boy Kotlin).
 * For full IDE features (completions, validation, navigation), use IntelliJ IDEA.
 */

import * as vscode from 'vscode';

let statusBarItem: vscode.StatusBarItem;

export function activate(context: vscode.ExtensionContext) {
    console.log('gbkt extension is now active');

    // Build ROM command
    const buildCmd = vscode.commands.registerCommand('gbkt.buildRom', () => {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (!workspaceFolder) {
            vscode.window.showErrorMessage('gbkt: No workspace folder open');
            return;
        }

        const terminal = vscode.window.createTerminal('gbkt Build');
        terminal.show();
        terminal.sendText('./gradlew buildRom');
    });

    // Status bar button
    statusBarItem = vscode.window.createStatusBarItem(
        vscode.StatusBarAlignment.Right,
        100
    );
    statusBarItem.text = '$(package) Build ROM';
    statusBarItem.command = 'gbkt.buildRom';
    statusBarItem.tooltip = 'Build Game Boy ROM (gbkt)';
    statusBarItem.show();

    context.subscriptions.push(buildCmd, statusBarItem);
}

export function deactivate() {
    statusBarItem?.dispose();
}
