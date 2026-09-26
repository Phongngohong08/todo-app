package category

import (
	"reflect"
	"testing"
)

func TestNormalize(t *testing.T) {
	got := Normalize([]string{"  Học tập ", "học tập", "WORK", "", "Gia đình", "other"})
	want := []string{"Học tập", "Gia đình"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v, want %v", got, want)
	}
}
