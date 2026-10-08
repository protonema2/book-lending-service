-- Demo data so the API can be explored right after `docker compose up`.
-- Member emails match the demo MEMBER users configured in application.yml.
INSERT INTO member (name, email) VALUES
    ('Alice Johnson', 'alice@example.com'),
    ('Bob Smith',     'bob@example.com'),
    ('Carol Diaz',    'carol@example.com');

INSERT INTO book (title, author, isbn, total_copies, available_copies) VALUES
    ('Clean Code',                                'Robert C. Martin',              '9780132350884', 3, 3),
    ('Effective Java',                            'Joshua Bloch',                  '9780134685991', 2, 2),
    ('Designing Data-Intensive Applications',     'Martin Kleppmann',              '9781449373320', 2, 2),
    ('The Pragmatic Programmer',                  'David Thomas, Andrew Hunt',     '9780135957059', 1, 1),
    ('Domain-Driven Design',                      'Eric Evans',                    '9780321125217', 1, 1),
    ('Refactoring',                               'Martin Fowler',                 '9780134757599', 2, 2),
    ('Java Concurrency in Practice',              'Brian Goetz',                   '9780321349606', 1, 1),
    ('Design Patterns',                           'Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides', '9780201633610', 2, 2),
    ('Spring in Action',                          'Craig Walls',                   '9781617294945', 3, 3),
    ('Head First Design Patterns',                'Eric Freeman, Elisabeth Robson', '9780596007126', 2, 2);
