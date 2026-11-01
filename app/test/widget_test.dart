import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:java_patterns/app_scope.dart';
import 'package:java_patterns/content/content_repository.dart';
import 'package:java_patterns/content/models.dart';
import 'package:java_patterns/main.dart';
import 'package:java_patterns/progress/progress_store.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A fake repository that reads the generated content straight from disk.
class DiskRepository implements ContentRepository {
  DiskRepository(this.catalogValue);
  final Catalog catalogValue;
  @override
  Future<Catalog> catalog() async => catalogValue;
  @override
  Future<String> markdown(PageMeta page) async => File('assets/content/${page.id}.md').readAsString();
}

Catalog loadCatalog() =>
    Catalog.fromJson(jsonDecode(File('assets/content/index.json').readAsStringSync()) as Map<String, dynamic>);

Future<Widget> buildApp(Catalog catalog) async {
  SharedPreferences.setMockInitialValues({});
  final progress = await ProgressStore.load();
  return AppScope(repository: DiskRepository(catalog), catalog: catalog, progress: progress, child: const JavaPatternsApp());
}

void main() {
  test('catalog has six levels and resolves relative links', () {
    final catalog = loadCatalog();
    expect(catalog.levels.map((l) => l.code), ['L0', 'L1', 'L2', 'L3', 'L4', 'L5']);
    final solid = catalog.byId('l1-foundations__solid-principles')!;
    expect(catalog.resolveLink(solid, 'oop-refresher.md')!.title, 'OOP Refresher for Patterns');
    expect(catalog.resolveLink(solid, '../l2-practitioner/visitor.md#x')!.id, 'l2-practitioner__visitor');
    expect(catalog.resolveLink(solid, 'https://openjdk.org/jeps/409'), isNull);
  });

  test('every written topic page has content without MkDocs-only syntax', () {
    final catalog = loadCatalog();
    for (final page in catalog.pages.where((p) => p.written)) {
      final md = File('assets/content/${page.id}.md').readAsStringSync();
      expect(md, isNot(contains('--8<--')), reason: page.id);
      expect(RegExp(r'^\s*(!!!|\?\?\?) ', multiLine: true).hasMatch(md), isFalse, reason: page.id);
      expect(RegExp(r'^\s*=== "', multiLine: true).hasMatch(md), isFalse, reason: page.id);
    }
  });

  test('progress store remembers read and saved pages', () async {
    SharedPreferences.setMockInitialValues({});
    final store = await ProgressStore.load();
    await store.toggleRead('a');
    await store.toggleSaved('b');
    final reloaded = await ProgressStore.load();
    expect(reloaded.isRead('a'), isTrue);
    expect(reloaded.isSaved('b'), isTrue);
    expect(reloaded.readCount(['a', 'b']), 1);
  });

  testWidgets('home shows the levels and opens a level', (tester) async {
    await tester.pumpWidget(await buildApp(loadCatalog()));
    await tester.pumpAndSettle();
    expect(find.text('Java Design Patterns'), findsOneWidget);
    expect(find.textContaining('Foundations'), findsWidgets);
    await tester.tap(find.textContaining('Orientation').first);
    await tester.pumpAndSettle();
    expect(find.text('What Are Design Patterns'), findsOneWidget);
  });
}
