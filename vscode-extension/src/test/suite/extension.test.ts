import * as assert from 'assert';
import * as vscode from 'vscode';

suite('gbkt Extension Test Suite', () => {
    vscode.window.showInformationMessage('Starting gbkt extension tests.');

    test('Extension should be present', () => {
        const extension = vscode.extensions.getExtension('gbkt.gbkt');
        assert.ok(extension, 'Extension should be installed');
    });

    test('Extension should activate', async () => {
        const extension = vscode.extensions.getExtension('gbkt.gbkt');
        if (extension) {
            await extension.activate();
            assert.ok(extension.isActive, 'Extension should be active');
        }
    });

    test('Build ROM command should be registered', async () => {
        const commands = await vscode.commands.getCommands(true);
        assert.ok(
            commands.includes('gbkt.buildRom'),
            'gbkt.buildRom command should be registered'
        );
    });
});
