// Package migrations nhúng các file *.up.sql vào binary để API tự áp schema khi khởi động
// (xem db.RunMigrations). Quy ước tên: <version 6 chữ số>_<mô tả>.up.sql, áp theo thứ tự version.
package migrations

import "embed"

//go:embed *.up.sql
var FS embed.FS
