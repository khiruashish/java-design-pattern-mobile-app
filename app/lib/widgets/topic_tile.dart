import 'package:flutter/material.dart';

import '../app_scope.dart';
import '../content/models.dart';
import '../screens/page_screen.dart';

class TopicTile extends StatelessWidget {
  const TopicTile({super.key, required this.page, this.showGroup = false});

  final PageMeta page;
  final bool showGroup;

  @override
  Widget build(BuildContext context) {
    final progress = AppScope.of(context).progress;
    final scheme = Theme.of(context).colorScheme;
    return ListenableBuilder(
      listenable: progress,
      builder: (context, _) {
        final read = progress.isRead(page.id);
        final subtitle = [
          if (showGroup && page.group.isNotEmpty) page.group.split('-').skip(1).join(' '),
          if (page.minutes != null) '${page.minutes} min',
          if (!page.written) 'coming soon',
        ].join(' · ');
        return ListTile(
          leading: Icon(
            read ? Icons.check_circle : (page.written ? Icons.article_outlined : Icons.hourglass_empty),
            color: read ? scheme.primary : (page.written ? scheme.onSurfaceVariant : scheme.outline),
          ),
          title: Text(page.title, style: TextStyle(color: page.written ? null : scheme.outline)),
          subtitle: subtitle.isEmpty ? null : Text(subtitle),
          trailing: progress.isSaved(page.id) ? Icon(Icons.bookmark, color: scheme.tertiary) : null,
          onTap: () => PageScreen.open(context, page),
        );
      },
    );
  }
}
