package db

import (
	"testing"
	"testing/fstest"
	"todo-backend/migrations"
)

func TestListMigrations_SortsByVersion(t *testing.T) {
	fsys := fstest.MapFS{
		"000010_ten.up.sql":   {Data: []byte("SELECT 1")},
		"000002_two.up.sql":   {Data: []byte("SELECT 1")},
		"000002_two.down.sql": {Data: []byte("SELECT 1")},
	}
	files, err := listMigrations(fsys)
	if err != nil {
		t.Fatal(err)
	}
	if len(files) != 2 || files[0].version != 2 || files[1].version != 10 {
		t.Fatalf("unexpected order: %+v", files)
	}
}

func TestListMigrations_RejectsDuplicatesAndBadNames(t *testing.T) {
	dup := fstest.MapFS{
		"000001_a.up.sql": {Data: []byte("")},
		"000001_b.up.sql": {Data: []byte("")},
	}
	if _, err := listMigrations(dup); err == nil {
		t.Fatal("duplicate version should be rejected")
	}
	bad := fstest.MapFS{"init.up.sql": {Data: []byte("")}}
	if _, err := listMigrations(bad); err == nil {
		t.Fatal("file without version prefix should be rejected")
	}
}

// Bảo đảm các migration nhúng trong binary liên tục 1..N và bao gồm baseline cũ.
func TestEmbeddedMigrations_AreContiguous(t *testing.T) {
	files, err := listMigrations(migrations.FS)
	if err != nil {
		t.Fatal(err)
	}
	if len(files) <= legacyBaselineVersion {
		t.Fatalf("expected more than %d migrations, got %d", legacyBaselineVersion, len(files))
	}
	for i, f := range files {
		if f.version != i+1 {
			t.Fatalf("migration versions must be contiguous from 1, got %d at position %d", f.version, i)
		}
	}
}
